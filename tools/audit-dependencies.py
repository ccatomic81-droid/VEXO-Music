"""Audit actual Maven dependency versions against the public OSV database. No credentials sent."""
from pathlib import Path
import json,sys,urllib.request,urllib.error
p=Path(sys.argv[1]);inventory=json.loads(p.read_text());queries=inventory['queries']
if not queries or len(queries)>1000:raise SystemExit('Invalid or empty dependency inventory')
request=urllib.request.Request('https://api.osv.dev/v1/querybatch',data=json.dumps(inventory).encode(),method='POST',headers={'Content-Type':'application/json','User-Agent':'VEXO-Security-Inventory/1'})
try:
 with urllib.request.urlopen(request,timeout=45) as response:
  data=response.read(4*1024*1024+1)
  if len(data)>4*1024*1024:raise ValueError('Response too large')
  results=json.loads(data)['results']
except Exception:raise SystemExit('Dependency advisory check unavailable; no clean-audit claim is made.')
if len(results)!=len(queries):raise SystemExit('Incomplete dependency advisory response')
findings=[]
for query,result in zip(queries,results):
 if 'error' in result:raise SystemExit('Dependency advisory query failed')
 if result.get('next_page_token'):raise SystemExit('Dependency advisory results are incomplete; further pagination is required')
 for vulnerability in result.get('vulns',[]):findings.append({'package':query['package']['name'],'version':query['version'],'advisory':vulnerability['id']})
report={'source':'https://api.osv.dev/v1/querybatch','resolved_packages':len(queries),'findings':findings,'scope':'Known OSV advisories for resolved Maven versions; not a complete security audit'}
(p.parent/'advisory-report.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
if findings:raise SystemExit('Known dependency advisories need review and remediation before this candidate is ready.')
