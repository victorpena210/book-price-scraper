// Dependency-free controller tests for the actual browser script with a small DOM fixture.
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
class Element {
  constructor(tag='div'){this.tagName=tag;this.children=[];this.textContent='';this.dataset={};this.events={};this.disabled=false;this.hidden=false;}
  append(...elements){this.children.push(...elements);}
  replaceChildren(...elements){this.children=elements;}
  addEventListener(name,fn){this.events[name]=fn;}
  click(){return this.events.click?.();}
}
function fixture({key=true,mode='ok',blocked=false}={}){
 const elements=new Map();
 const get=id=>{if(!elements.has(id))elements.set(id,new Element());return elements.get(id);};
 const person=(id,name,city='Austin')=>({personId:id,fullName:name,residenceCity:city,residenceState:'TX',relationship:'sibling',deceasedName:'Example'});
 const rows=[person(1,'Taylor Example'),person(2,'Taylor Example'),person(3,'Casey Example','Dallas')];
 const state={calls:[],mode,key,release:null,version:'2026-10-08.3'};
 const all=[];
 const document={getElementById:get,createElement:tag=>{const e=new Element(tag);all.push(e);return e;},createDocumentFragment:()=>new Element('fragment'),querySelectorAll:()=>all.filter(e=>e.dataset.testPerson)};
 const context=vm.createContext({document,console,Blob,URL,Date,setTimeout:fn=>setTimeout(fn,0),fetch:async(url,options)=>{
  if(url.endsWith('/records'))return {ok:true,json:async()=>({apiKeyConfigured:state.key,totalPeople:rows.length,totalObituaries:2,people:rows,lookupVersion:state.version})};
  const id=JSON.parse(options.body).personIds[0];state.calls.push(id);
  if(blocked)await new Promise(resolve=>state.release=resolve);
  const failed=['ge08','ue03'].includes(state.mode), code=state.mode.toUpperCase();
  const result={person:rows.find(p=>p.personId===id),lookup:failed?null:{status:'NO_MATCH',candidates:[],resultCodes:['UE01']},error:failed?'MELISSA_API_ERROR':'',message:failed?`${code}: Lookup failed`:'No match',resultCodes:failed?[code]:['UE01']};
  return {ok:true,json:async()=>({results:[result],apiRequests:1,stopped:failed,message:result.message})};
 }});
 vm.runInContext(fs.readFileSync(path.join(__dirname,'../src/main/resources/static/saved-people.js'),'utf8'),context);
 return {context,state,get,all,rows,ready:()=>waitUntil(()=>!get('reload').disabled)};
}
async function waitUntil(predicate){for(let i=0;i<100;i++){if(predicate())return;await new Promise(r=>setTimeout(r,2));}throw new Error('fixture did not settle');}
test('loading is read-only; single then all deduplicates saved names across runs',async()=>{
 const f=fixture();await f.ready();assert.deepEqual(f.state.calls,[]);
 await f.get('test-first').click();assert.deepEqual(f.state.calls,[1]);
 await f.get('test-all').click();assert.deepEqual(f.state.calls,[1,3]);assert.equal(f.get('tested-count').textContent,3);
});
test('GE08 stops all remaining names and allows retry',async()=>{
 const f=fixture({mode:'ge08'});await f.ready();await f.get('test-all').click();
 assert.deepEqual(f.state.calls,[1]);assert.equal(f.get('tested-count').textContent,0);assert.match(f.get('error').textContent,/GE08/);
 f.state.mode='ok';await f.get('test-first').click();assert.deepEqual(f.state.calls,[1,1]);
});
test('stop waits for in-flight lookup and sends no next lookup',async()=>{
 const f=fixture({blocked:true});await f.ready();const run=f.get('test-all').click();await waitUntil(()=>f.state.release);
 f.get('stop').click();f.state.release();await run;assert.deepEqual(f.state.calls,[1]);assert.equal(f.get('tested-count').textContent,1);
});
test('missing key disables paid controls but retains saved list',async()=>{
 const f=fixture({key:false});await f.ready();assert.equal(f.get('test-all').disabled,true);assert.equal(f.get('people-count').textContent,3);
 assert.deepEqual(f.state.calls,[]);assert.ok(f.all.filter(e=>e.dataset.testPerson).every(e=>e.disabled));
});
test('saved names are rendered as text; input is never interpreted as HTML',async()=>{
 const f=fixture();await f.ready();f.rows[0].fullName='<img src=x onerror="alert(1)">';await f.get('reload').click();
 assert.ok(f.all.some(e=>e.textContent===f.rows[0].fullName));assert.ok(f.all.every(e=>e.tagName!=='img'));assert.deepEqual(f.state.calls,[]);
});

test('UE03 stops before the next name and does not mark it completed',async()=>{
 const f=fixture({mode:'ue03'});await f.ready();await f.get('test-ten').click();
 assert.deepEqual(f.state.calls,[1]);assert.equal(f.get('tested-count').textContent,0);
 assert.match(f.get('error').textContent,/UE03/);assert.equal(f.get('download').disabled,false);
});
test('updated backend version clears stale session results on reload without another lookup',async()=>{
 const f=fixture();await f.ready();assert.match(f.get('lookup-version').textContent,/2026-10-08.3/);
 await f.get('test-all').click();assert.equal(f.get('tested-count').textContent,3);
 f.state.version='2026-10-08.4';await f.get('reload').click();
 assert.equal(f.get('tested-count').textContent,0);assert.equal(f.get('request-count').textContent,0);
 assert.deepEqual(f.state.calls,[1,3]);
 await f.get('test-first').click();assert.deepEqual(f.state.calls,[1,3,1]);
});
