'use strict';
const $ = id => document.getElementById(id);
let people = [], configured = false, busy = false, stopRequested = false, attempts = 0;
let lookupVersion = '';
const results = new Map(), completed = new Set(), queryCache = new Map(), rowCells = new Map();
const normalized = value => (value || '').trim().replace(/\s+/g, ' ').toLowerCase();
const queryKey = p => JSON.stringify([p.fullName, p.residenceCity, p.residenceState].map(normalized));
const pending = () => people.filter(p => !completed.has(p.personId));
const make = (tag, text, className) => { const e = document.createElement(tag); e.textContent = text; if (className) e.className = className; return e; };

function controls() {
  $('test-first').disabled = busy || !configured || pending().length === 0;
  $('test-ten').disabled = $('test-first').disabled;
  $('test-all').disabled = $('test-first').disabled;
  $('stop').disabled = !busy || stopRequested;
  $('reload').disabled = busy;
  $('download').disabled = results.size === 0 || busy;
  document.querySelectorAll('[data-test-person]').forEach(b => b.disabled = busy || !configured);
  $('tested-count').textContent = completed.size;
  $('request-count').textContent = attempts;
}

async function api(path, options) {
  const response = await fetch(path, {cache:'no-store', ...options});
  let body;
  try { body = await response.json(); } catch { throw new Error('The app returned an unreadable response. Check its console.'); }
  if (!response.ok) throw new Error(body.message || body.detail || `Request failed (HTTP ${response.status}).`);
  return body;
}

function showError(message) { $('error').textContent = message; $('error').hidden = !message; }

function renderResult(personId) {
  const cell = rowCells.get(personId), result = results.get(personId);
  if (!cell || !result) return;
  cell.replaceChildren();
  const lookup = result.lookup;
  const label = lookup ? lookup.status.replaceAll('_', ' ').toLowerCase() : 'Lookup error';
  cell.append(make('div', label, 'result-status'), make('small', result.message || ''));
  if (result.resultCodes?.length) cell.append(make('div', result.resultCodes.join(', '), 'codes'));
  if (result.reusedResult) cell.append(make('small', 'Shared result for the same name and location.'));
  if (lookup?.candidates?.length) {
    const details = make('details', '', 'result-detail');
    details.append(make('summary', `${lookup.returnedRecords} of ${lookup.totalRecords} candidates`));
    for (const c of lookup.candidates) {
      details.append(make('div', [c.fullName, [c.addressLine1,c.suite].filter(Boolean).join(' '),
        [c.city,c.state,c.postalCode].filter(Boolean).join(', '),
        c.phoneNumbers?.length ? c.phoneNumbers.join(', ') : 'No phone returned'].filter(Boolean).join('\n'), 'candidate'));
    }
    if (lookup.morePagesAvailable) details.append(make('small', 'More candidate pages are available; this test fetched only the first page.'));
    cell.append(details);
  }
}

function renderPeople() {
  const fragment = document.createDocumentFragment();
  rowCells.clear();
  for (const p of people) {
    const row = document.createElement('tr'), name = document.createElement('td');
    name.append(make('strong', p.fullName), make('small', `ID ${p.personId} · ${p.relationship || 'Relationship not recorded'}`));
    name.append(make('small', `Obituary: ${p.deceasedName || 'Not recorded'}`));
    if (p.phoneNumber) name.append(make('small', `Saved phone: ${p.phoneNumber}`));
    const location = make('td', [p.residenceCity,p.residenceState].filter(Boolean).join(', ') || 'Not recorded');
    const result = make('td', 'Not tested this session');
    rowCells.set(p.personId, result);
    const action = document.createElement('td'), button = make('button', 'Test name', 'secondary');
    button.dataset.testPerson = p.personId;
    button.addEventListener('click', () => run([p], true));
    action.append(button); row.append(name,location,result,action); fragment.append(row);
  }
  if (!people.length) {
    const row = document.createElement('tr'), cell = make('td', 'No saved people were found in the connected database.');
    cell.colSpan = 4; row.append(cell); fragment.append(row);
  }
  $('people').replaceChildren(fragment);
  results.forEach((_, id) => renderResult(id));
}

async function load() {
  busy = true; controls(); showError('');
  try {
    const data = await api('/api/people/melissa/records');
    if (lookupVersion && lookupVersion !== data.lookupVersion) {
      results.clear(); completed.clear(); queryCache.clear(); attempts = 0;
    }
    lookupVersion = data.lookupVersion || 'older version';
    $('lookup-version').textContent = `Lookup update: ${lookupVersion}`;
    people = data.people; configured = data.apiKeyConfigured;
    const current = new Map(people.map(p => [p.personId,p]));
    for (const [id,result] of results) {
      if (!current.has(id) || queryKey(current.get(id)) !== queryKey(result.person)) {
        results.delete(id); completed.delete(id);
      }
    }
    $('people-count').textContent = data.totalPeople;
    $('obituary-count').textContent = data.totalObituaries;
    $('key-status').textContent = configured
      ? 'Melissa key is configured. Test one name to check that the service accepts it.'
      : 'Melissa key is missing. Add MELISSA_API_KEY to the app’s run configuration, then restart the app.';
    $('progress').textContent = `${people.length} saved records loaded. ${pending().length} remain to test this session.`;
    renderPeople();
  } catch (error) { configured = false; showError(error.message); $('progress').textContent = 'Could not load saved records.'; }
  finally { busy = false; controls(); }
}

async function run(selection, force = false) {
  if (busy || !configured || !selection.length) return;
  busy = true; stopRequested = false; controls(); showError('');
  let processed = 0, stopped = false;
  $('progress-bar').hidden = false; $('progress-bar').value = 0; $('progress-bar').max = selection.length;
  try {
    // One saved name per request gives a progress update and a stop point after each call.
    for (const p of selection) {
      if (stopRequested) { stopped = true; break; }
      $('progress').textContent = `Testing ${processed + 1} of ${selection.length}: ${p.fullName}`;
      const cached = !force && queryCache.get(queryKey(p));
      let result;
      if (cached) { result = {...cached, person:p, reusedResult:true}; }
      else {
        attempts++; controls();
        const batch = await api('/api/people/melissa/test', {method:'POST', headers:{'Content-Type':'application/json'},
          body:JSON.stringify({personIds:[p.personId]})});
        result = batch.results[0];
        if (!result) throw new Error(batch.message || 'No result was returned. The test has stopped.');
        results.set(p.personId,result); renderResult(p.personId);
        if (batch.stopped) { stopped = true; showError(batch.message); break; }
        queryCache.set(queryKey(p),result);
      }
      results.set(p.personId,result); completed.add(p.personId); renderResult(p.personId);
      processed++; $('progress-bar').value = processed; controls();
      if (processed < selection.length && !stopRequested) await new Promise(resolve => setTimeout(resolve,250));
    }
  } catch (error) { stopped = true; showError(error.message); }
  finally {
    busy = false;
    $('progress').textContent = `${stopped || stopRequested ? 'Stopped' : 'Finished'}: ${processed} of ${selection.length} records completed. ${pending().length} remain this session.`;
    controls();
  }
}

$('test-first').addEventListener('click', () => run(pending().slice(0,1)));
$('test-ten').addEventListener('click', () => run(pending().slice(0,10)));
$('test-all').addEventListener('click', () => run(pending()));
$('stop').addEventListener('click', () => { stopRequested = true; $('progress').textContent = 'Stopping after the current lookup returns…'; controls(); });
$('reload').addEventListener('click', load);
$('download').addEventListener('click', () => {
  const data = {exportedAt:new Date().toISOString(), lookupVersion, lookupAttempts:attempts, results:[...results.values()]};
  const url = URL.createObjectURL(new Blob([JSON.stringify(data,null,2)],{type:'application/json'}));
  const link = document.createElement('a'); link.href = url; link.download = 'melissa-test-results.json'; link.click();
  setTimeout(() => URL.revokeObjectURL(url),1000);
});
load();
