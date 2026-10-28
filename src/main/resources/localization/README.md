# Portuguese geographic labels

Country names use `names.translations.por.common` from the REST Countries v5 response.
If absent, ISO alpha-2 codes resolve Portuguese names with Java locale data. A small set of
non-ISO country aliases is defined in PortugueseGeography.

Capital labels are bundled in capitals-pt.json, so playing a round never calls a translation service.
Initial labels were retrieved on 2026-10-28 from Wikidata (CC0 structured data):
https://www.wikidata.org/wiki/Wikidata:Licensing
https://query.wikidata.org/

Extraction query:

    SELECT DISTINCT ?code ?en ?pt WHERE {
      ?country wdt:P297 ?code; wdt:P36 ?city.
      ?city rdfs:label ?en FILTER(LANG(?en)="en").
      ?city rdfs:label ?pt FILTER(LANG(?pt)="pt").
    } LIMIT 1000

The file additionally includes spelling aliases seen in the application's real REST Countries
catalog (for example St. Peter Port and City of San Marino), territories absent from the query,
and Brazilian forms such as Amsterdã, Moscou and Teerã. Identical spellings in both languages are
explicit entries, not automatic English fallbacks. Punctuation and accents are normalized only
for lookup; the catalog value is displayed unchanged.

Unknown country/capital translations are excluded from Portuguese question generation rather than
mixed with English. Keep the English API names unchanged and add the missing Portuguese labels.
Tests cover both question directions and missing-data behavior. English play uses the source names.

API schema reference: https://restcountries.com/docs/countries
