"""Preserve each released Maven archive when assembling the public Pages repository."""
import argparse
import json
import re
import subprocess
import zipfile
from pathlib import Path

parser=argparse.ArgumentParser();parser.add_argument('tag');args=parser.parse_args()
if not re.fullmatch(r'v\d+\.\d+\.\d+(?:-[a-zA-Z0-9.]+)?',args.tag):raise ValueError('Expected a library version tag')
root=Path(__file__).resolve().parents[1];site=root/'build/site';maven=site/'maven'
archive=root/'build/maven-repository.zip'
with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9)as writer:
    for source in sorted(maven.rglob('*')):
        if source.is_file():
            entry=zipfile.ZipInfo(source.relative_to(site).as_posix(),(2026,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED
            writer.writestr(entry,source.read_bytes())
releases=json.loads(subprocess.check_output(['gh','release','list','--repo','JustNeoNixy/SpeechLib','--limit','1000','--json','tagName'],text=True))
for release in releases:
    tag=release['tagName']
    if tag==args.tag or not re.fullmatch(r'v\d+\.\d+\.\d+(?:-[a-zA-Z0-9.]+)?',tag):continue
    folder=root/'build/previous-maven'/tag;folder.mkdir(parents=True,exist_ok=True)
    assets=json.loads(subprocess.check_output(['gh','release','view',tag,'--repo','JustNeoNixy/SpeechLib','--json','assets'],text=True))['assets']
    if not any(asset['name']=='maven-repository.zip'for asset in assets):raise RuntimeError('Previous library release has no Maven archive: '+tag)
    subprocess.run(['gh','release','download',tag,'--repo','JustNeoNixy/SpeechLib','--pattern','maven-repository.zip','--dir',str(folder),'--clobber'],check=True)
    with zipfile.ZipFile(folder/'maven-repository.zip')as previous:
        for entry in previous.infolist():
            target=(site/entry.filename).resolve()
            if not target.is_relative_to(site.resolve())or not entry.filename.startswith('maven/'):raise RuntimeError('Invalid Maven archive path')
            if entry.is_dir()or target.exists():continue
            target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(previous.read(entry))
subprocess.run(['gh','release','upload',args.tag,str(archive),'--repo','JustNeoNixy/SpeechLib','--clobber'],check=True)
(site/'index.html').write_text('<!doctype html><meta charset="utf-8"><title>SpeechLib Maven repository</title><h1>SpeechLib</h1><p>Maven repository: <code>https://justneonixy.github.io/SpeechLib/maven</code></p><p><a href="https://github.com/JustNeoNixy/SpeechLib">Source, setup and API examples</a></p>\n',encoding='utf-8')
