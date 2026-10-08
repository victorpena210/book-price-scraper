#!/usr/bin/env bash
set -euo pipefail

update_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
existing_dir="${1:-$HOME/Desktop/IdeaProjects/Spring-Projects/book-price-scraper}"
if [[ ! -f "$existing_dir/pom.xml" || ! -d "$existing_dir/src/main/java/com/victorpena/bookpricescraper" ]]; then
  echo "Could not find the original book-price-scraper project at: $existing_dir" >&2
  echo "Pass the original project directory as the first argument." >&2
  exit 1
fi
existing_dir="$(cd "$existing_dir" && pwd)"
destination="${2:-$(dirname "$existing_dir")/contact-tracker}"
destination_parent="$(cd "$(dirname "$destination")" && pwd)"
destination="$destination_parent/$(basename "$destination")"
if [[ -e "$destination" || -L "$destination" ]]; then
  echo "Destination already exists; nothing was changed: $destination" >&2
  echo "Choose an unused destination as the second argument." >&2
  exit 1
fi
for file in pom.xml .project src/main/java/com/victorpena/contacttracker/ContactTrackerApplication.java src/main/resources/static/index.html; do
  [[ -f "$update_root/$file" ]] || { echo "Renamed project is incomplete: $file" >&2; exit 1; }
done
properties_relative="src/main/resources/application.properties"
[[ -f "$existing_dir/$properties_relative" ]] || { echo "Existing database configuration is missing." >&2; exit 1; }

staging_parent="$(mktemp -d "$destination_parent/contact-tracker-install-XXXXXXXX")"
stage="$staging_parent/contact-tracker"
cp -pR "$existing_dir" "$stage"
cp -p "$existing_dir/$properties_relative" "$staging_parent/original-application.properties"

# Rename copied source directories, including any additional local Java files.
# The original project is never modified.
for source_set in main test; do
  old_package="$stage/src/$source_set/java/com/victorpena/bookpricescraper"
  new_package="$stage/src/$source_set/java/com/victorpena/contacttracker"
  if [[ -d "$old_package" ]]; then
    [[ ! -e "$new_package" ]] || { echo "Both Java package names exist; original project unchanged. Check $stage" >&2; exit 1; }
    while IFS= read -r -d '' file; do
      awk '{gsub(/com\.victorpena\.bookpricescraper/, "com.victorpena.contacttracker"); gsub(/BookPriceScraperApplication/, "ContactTrackerApplication"); print}' "$file" > "$file.rename-tmp"
      mv "$file.rename-tmp" "$file"
    done < <(find "$old_package" -type f -name '*.java' -print0)
    mv "$old_package" "$new_package"
    for suffix in '' Tests; do
      old_class="$new_package/BookPriceScraperApplication${suffix}.java"
      if [[ -f "$old_class" ]]; then
        mv "$old_class" "$new_package/ContactTrackerApplication${suffix}.java"
      fi
    done
  fi
done

# Keep old compiled classes out of the renamed application's classpath.
if [[ -d "$stage/target" ]]; then mv "$stage/target" "$staging_parent/previous-build"; fi
cp -pR "$update_root/." "$stage/"

# Preserve the live configuration, changing only the application display name.
awk '
  /^[[:space:]]*spring[.]application[.]name[[:space:]]*=/ {print "spring.application.name=contact-tracker"; found=1; next}
  {print}
  END {if (!found) print "spring.application.name=contact-tracker"}
' "$staging_parent/original-application.properties" > "$stage/$properties_relative"

mv "$stage" "$destination"
echo "Contact Tracker installed in: $destination"
echo "Original project preserved in: $existing_dir"
echo "Import contact-tracker as an Existing Maven Project in Spring Tools."
echo "Edit the previous run configuration: Project=contact-tracker; Main type=com.victorpena.contacttracker.ContactTrackerApplication."
echo "Keep its Environment values, apply, and run. Open http://localhost:8080/"
