"""Compara módulos integrados con los ZIP extraídos, sin ejecutar ni descargar modelos."""
from argparse import ArgumentParser
from pathlib import Path
import difflib
import hashlib
import json

root = Path(__file__).resolve().parents[1]
parser = ArgumentParser(description=__doc__)
parser.add_argument("origen", type=Path, help="Carpeta que contiene Audio y Cotidiano")
origen = parser.parse_args().origen.resolve()
sources = {
    "audioToHaptics": origen / "Audio/Tesis-main/Tesis-main/AudioToHaptics/audioToHaptics",
    "audioToHapticsModels": origen / "Audio/Tesis-main/Tesis-main/AudioToHaptics/audioToHapticsModels",
    "sound-engine": origen / "Cotidiano/sound-recognition-main/sound-recognition-main/Android/sound-engine",
}
reports = root.parent / "Informes/auditoria"
reports.mkdir(parents=True, exist_ok=True)
records = []
for module, source in sources.items():
    if not source.is_dir():
        raise SystemExit(f"No se encuentra el origen: {source}")
    for original in sorted(source.rglob("*")):
        relative = original.relative_to(source)
        if not original.is_file() or any(p in {"build", ".gradle", ".kotlin"} for p in relative.parts):
            continue
        target = root / module / relative
        a = original.read_bytes()
        b = target.read_bytes() if target.is_file() else b""
        same = a == b
        item = {
            "archivo": f"{module}/{relative.as_posix()}",
            "estado": "identico" if same else "modificado" if target.is_file() else "ausente",
            "sha256_origen": hashlib.sha256(a).hexdigest(),
            "sha256_integrado": hashlib.sha256(b).hexdigest() if target.is_file() else None,
        }
        if not same and original.suffix in {".kt", ".kts", ".xml", ".json", ".txt", ".pro"}:
            diff = "".join(difflib.unified_diff(a.decode("utf-8-sig").splitlines(keepends=True), b.decode("utf-8-sig").splitlines(keepends=True), fromfile="original/" + item["archivo"], tofile="integrado/" + item["archivo"]))
            path = reports / (module + "_" + relative.as_posix().replace("/", "_") + ".diff")
            path.write_text(diff, encoding="utf-8")
            item["diff"] = path.relative_to(root).as_posix()
        records.append(item)

unexpected = []
for item in records:
    if "/src/main/" in item["archivo"] and item["estado"] != "identico":
        if item["archivo"] != "sound-engine/src/main/java/com/indoamerica/haptica/soundengine/SoundEngine.kt":
            unexpected.append(item["archivo"])
(reports / "comparacion_fuentes.json").write_text(json.dumps(records, ensure_ascii=False, indent=2), encoding="utf-8")
summary = {"archivos_comparados": len(records), "identicos": sum(r["estado"] == "identico" for r in records), "modificados": [r["archivo"] for r in records if r["estado"] != "identico"], "cambios_no_previstos_en_produccion": unexpected}
(reports / "resumen.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps(summary, ensure_ascii=False, indent=2))
if unexpected:
    raise SystemExit(1)
