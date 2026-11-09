"""Package ordinary GPU libraries with licenses and relocatable loader metadata."""
import argparse
import ctypes
import hashlib
import json
import os
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('build', type=Path)
parser.add_argument('platform')
parser.add_argument('backend', choices=('vulkan','metal'))
parser.add_argument('--source', required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
output = root/'build/gpu-artifact'/args.platform
output.mkdir(parents=True, exist_ok=True)
extension = '.dll' if args.platform.startswith('windows') else '.dylib' if args.platform.startswith('macos') else '.so'
for source in args.build.rglob('*'):
    if not source.is_file() or 'speechlib-' not in source.name: continue
    if not (source.name.endswith(extension) or (extension == '.so' and '.so.' in source.name)): continue
    target = output/source.name
    if target.exists() and target.read_bytes() != source.read_bytes(): raise RuntimeError('Conflicting native names')
    shutil.copyfile(source, target)
bridge = 'speechlib-gpu'+extension
backend = 'ggml-metal' if args.backend == 'metal' else 'ggml-vulkan'
prefix = '' if extension == '.dll' else 'lib'
preload = [f'{prefix}speechlib-{name}{extension}' for name in ('ggml-base','ggml-cpu',backend,'ggml','whisper')]
if not all((output/name).is_file() for name in [bridge]+preload): raise RuntimeError('Missing native library')
if sys.platform == 'darwin':
    files = {p.name:p for p in output.iterdir()}
    for native in files.values():
        for line in subprocess.check_output(['otool','-L',str(native)],text=True).splitlines()[1:]:
            dependency = line.strip().split(' (')[0]
            if Path(dependency).name in files:
                subprocess.run(['install_name_tool','-change',dependency,'@loader_path/'+Path(dependency).name,str(native)],check=True)
        subprocess.run(['codesign','--force','--sign','-',str(native)],check=True)
metadata = dict(backend=args.backend, library=bridge, preload=preload,
                bridgeVersion='speechlib-whisper-d1be6fde-v1', speechlibRevision=args.source,
                whisperRevision='d1be6fde11ac6e0407606b4e42fe72d34add8037')
(output/'source.json').write_text(json.dumps(metadata,indent=2)+'\n',encoding='utf-8')
shutil.copyfile(root/'build/whisper-source/LICENSE', output/'whisper-MIT.txt')
handles=[]
if os.name == 'nt': handles.append(os.add_dll_directory(str(output.resolve())))
for name in preload: ctypes.CDLL(str((output/name).resolve()))
api = ctypes.CDLL(str((output/bridge).resolve()))
api.speechlib_gpu_version.restype = ctypes.c_char_p
if api.speechlib_gpu_version() != b'speechlib-whisper-d1be6fde-v1': raise RuntimeError('Wrong bridge ABI')
for symbol in ('speechlib_gpu_device','speechlib_gpu_load','speechlib_gpu_free','speechlib_gpu_transcribe'): getattr(api,symbol)
archive = output.parent/f'speechlib-gpu-{args.platform}.zip'
files = {}
with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as writer:
    for source in sorted(output.iterdir()):
        payload = source.read_bytes(); files[source.name]=hashlib.sha256(payload).hexdigest()
        entry = zipfile.ZipInfo(source.name,(2026,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED
        writer.writestr(entry,payload)
spec=dict(url=f'https://github.com/JustNeoNixy/SpeechLib/releases/download/runtime-gpu-1.0.0/{archive.name}',
          bytes=archive.stat().st_size,sha256=hashlib.sha256(archive.read_bytes()).hexdigest(),files=files,backend=args.backend)
(output.parent/f'{args.platform}.json').write_text(json.dumps(spec,indent=2)+'\n',encoding='utf-8')
print(f'Verified {args.backend} ABI and packaged {args.platform}: {archive.stat().st_size} bytes')
