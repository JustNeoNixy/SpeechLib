"""Preserve each released Maven archive when assembling the public Pages repository."""
import argparse
import json
import re
import subprocess
import zipfile
import hashlib
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path

parser=argparse.ArgumentParser();parser.add_argument('tag');args=parser.parse_args()
if not re.fullmatch(r'v\d+\.\d+\.\d+(?:-[a-zA-Z0-9.]+)?',args.tag):raise ValueError('Expected a library version tag')
root=Path(__file__).resolve().parents[1];site=root/'build/site';maven=site/'maven'
def unpack(path):
    with zipfile.ZipFile(path)as archive:
        for entry in archive.infolist():
            target=(site/entry.filename).resolve()
            if not target.is_relative_to(site.resolve())or not entry.filename.startswith('maven/'):raise RuntimeError('Invalid Maven archive path')
            if entry.is_dir()or target.exists():continue
            target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(archive.read(entry))
current=root/'build/current-maven';current.mkdir(parents=True,exist_ok=True)
subprocess.run(['gh','release','download',args.tag,'--repo','JustNeoNixy/SpeechLib','--pattern','maven-repository.zip','--dir',str(current),'--clobber'],check=True)
unpack(current/'maven-repository.zip')
releases=json.loads(subprocess.check_output(['gh','release','list','--repo','JustNeoNixy/SpeechLib','--limit','1000','--json','tagName'],text=True))
for release in releases:
    tag=release['tagName']
    if tag==args.tag or not re.fullmatch(r'v\d+\.\d+\.\d+(?:-[a-zA-Z0-9.]+)?',tag):continue
    folder=root/'build/previous-maven'/tag;folder.mkdir(parents=True,exist_ok=True)
    assets=json.loads(subprocess.check_output(['gh','release','view',tag,'--repo','JustNeoNixy/SpeechLib','--json','assets'],text=True))['assets']
    if not any(asset['name']=='maven-repository.zip'for asset in assets):raise RuntimeError('Previous library release has no Maven archive: '+tag)
    subprocess.run(['gh','release','download',tag,'--repo','JustNeoNixy/SpeechLib','--pattern','maven-repository.zip','--dir',str(folder),'--clobber'],check=True)
    unpack(folder/'maven-repository.zip')
artifact=maven/'dev/speechlib/speechlib'
versions=sorted((p.name for p in artifact.iterdir() if p.is_dir() and re.fullmatch(r'\d+\.\d+\.\d+(?:-[a-zA-Z0-9.]+)?',p.name)),key=lambda v:tuple(int(n)for n in v.split('-')[0].split('.'))+('-'not in v,v))
metadata=ET.Element('metadata')
ET.SubElement(metadata,'groupId').text='dev.speechlib';ET.SubElement(metadata,'artifactId').text='speechlib'
versioning=ET.SubElement(metadata,'versioning');ET.SubElement(versioning,'latest').text=versions[-1]
stable=[v for v in versions if '-'not in v]
if stable:ET.SubElement(versioning,'release').text=stable[-1]
entries=ET.SubElement(versioning,'versions')
for version in versions:ET.SubElement(entries,'version').text=version
ET.SubElement(versioning,'lastUpdated').text=datetime.now(timezone.utc).strftime('%Y%m%d%H%M%S')
payload=ET.tostring(metadata,encoding='utf-8',xml_declaration=True)
target=artifact/'maven-metadata.xml';target.write_bytes(payload)
for algorithm in ('md5','sha1','sha256','sha512'):Path(str(target)+'.'+algorithm).write_text(hashlib.new(algorithm,payload).hexdigest(),encoding='ascii')
(site/'index.html').write_text('<!doctype html><meta charset="utf-8"><title>SpeechLib Maven repository</title><h1>SpeechLib</h1><p>Maven repository: <code>https://justneonixy.github.io/SpeechLib/maven</code></p><p><a href="https://github.com/JustNeoNixy/SpeechLib">Source, setup and API examples</a></p>\n',encoding='utf-8')
