#!/usr/bin/env python3
"""Download official released Minecraft 1.21.1 mods for transient CI validation.
Do NOT commit JARs, PNGs or extracted upstream assets into this repository.
They remain in the runner temporary directory, removed when the job ends.
"""
import hashlib
import json
import os
import pathlib
import time
import urllib.error
import urllib.parse
import urllib.request

OUT=pathlib.Path(os.environ.get('RUNNER_TEMP','/tmp'))/'rpms-official-mods'
OUT.mkdir(parents=True,exist_ok=True)
API="https://api.modrinth.com/v2"
HEADERS={'User-Agent':'reme-mc-studio-testing/0.1 (GitHub Actions automated compatibility audit)',
         'Accept':'application/json'}

def retrieve(url):
    err=None
    for attempt in range(4):
        try:
            req=urllib.request.Request(url,headers=HEADERS)
            with urllib.request.urlopen(req,timeout=100) as response:
                return response.read()
        except (urllib.error.URLError, TimeoutError) as e:
            err=e
            print('Download attempt failed:',type(e).__name__,str(e)[:180],flush=True)
            time.sleep(2*(attempt+1))
    raise RuntimeError('Official provider fetch failed: '+str(err))
def download_version(slug,required,filename):
    query=urllib.parse.urlencode({'loaders':json.dumps(['neoforge']),
       'game_versions':json.dumps(['1.21.1'])})
    versions=json.loads(retrieve(f'{API}/project/{slug}/version?{query}'))
    matches=[v for v in versions if required in v['version_number']]
    if not matches:
        raise RuntimeError(f'Official Modrinth metadata has no matching version {slug} {required}')
    matches.sort(key=lambda v:(v['version_type']!='release',v['date_published']))
    v=matches[0]
    files=sorted(v['files'],key=lambda f:not f.get('primary',False))
    if not files:raise RuntimeError('Version has no official files')
    f=files[0]
    if not f['url'].startswith('https://'):raise RuntimeError('Refusing insecure URL')
    data=retrieve(f['url'])
    expected=f['hashes'].get('sha512') or f['hashes'].get('sha1')
    algo='sha512' if f['hashes'].get('sha512') else 'sha1'
    actual=hashlib.new(algo,data).hexdigest()
    if actual!=expected:raise RuntimeError(f'Official SHA checksum mismatch for {slug}')
    target=OUT/filename
    target.write_bytes(data)
    print(f'VALIDATED official {slug} {v["version_number"]}; bytes={len(data)}; sha512={hashlib.sha512(data).hexdigest()}',flush=True)
    return {'slug':slug,'version':v['version_number'],'sha512':hashlib.sha512(data).hexdigest(),
        'filename':str(target),'bytes':len(data),'source_id':v['id']}
record={'minecraft':'1.21.1','neoforge':True,'mods':[
    download_version('create','6.0.10','create-1.21.1.jar'),
    download_version('create-aeronautics','1.3.2','aeronautics-1.21.1.jar')]}
(OUT/'versions.json').write_text(json.dumps(record,indent=2),encoding='utf-8')
print('OFFICIAL_MOD_DIR='+str(OUT),flush=True)
