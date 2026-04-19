Place an extracted Vosk Android-compatible offline model here before running on a device.

Expected folder:
models/vosk-model-small-en-us-0.15/

Example model source:
https://alphacephei.com/vosk/models

After extraction, this directory should contain files like:
- am/final.mdl
- conf/model.conf
- graph/Gr.fst

Do not rename the folder unless you also update `MODEL_ASSET_PATH` in
`SilentAssistantService.kt`.
