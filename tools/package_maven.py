"""Archive the tested Maven publication so Pages promotes exactly the released jars."""
from pathlib import Path
import zipfile
import hashlib

root=Path(__file__).resolve().parents[1]
version=next(line.split('=',1)[1].strip()for line in (root/'gradle.properties').read_text().splitlines()if line.startswith('version='))
site=root/'build/site';artifact=site/'maven/dev/speechlib/speechlib';directory=artifact/version
if not directory.is_dir():raise RuntimeError('Run gradlew build publishAllPublicationsToSiteRepository first')
output=root/'build/maven-repository.zip'
files=sorted(p for p in artifact.iterdir()if p.is_file())+sorted(p for p in directory.rglob('*')if p.is_file())
for source in directory.glob('*.jar'):
    released=root/'build/libs'/source.name
    if not released.is_file()or hashlib.sha256(released.read_bytes()).digest()!=hashlib.sha256(source.read_bytes()).digest():raise RuntimeError('Maven and release jar differ: '+source.name)
with zipfile.ZipFile(output,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9)as writer:
    for source in files:
        entry=zipfile.ZipInfo(source.relative_to(site).as_posix(),(2026,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED
        writer.writestr(entry,source.read_bytes())
print(output)
