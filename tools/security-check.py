"""Small source guard; not a substitute for a full secret or dependency audit."""
from pathlib import Path
import re,subprocess,sys
patterns=[re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),re.compile(rb'gh[pousr]_[A-Za-z0-9]{30,}'),re.compile(rb'GOCSPX-[A-Za-z0-9_-]{20,}')]
paths=subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard','-z']).decode().split('\0')
fail=[]
for name in paths:
 p=Path(name)
 if not name or not p.is_file():continue
 if p.suffix.lower() in {'.keystore','.jks','.p12','.pfx'}:fail.append((name,'signing file'));continue
 if p.stat().st_size>2*1024*1024:continue
 if any(x.search(p.read_bytes()) for x in patterns):fail.append((name,'possible credential'))
for name,kind in fail:print(kind+': '+name)
if fail:sys.exit(1)
print('Tracked source credential guard passed. No private signing files or configured credential patterns found.')
