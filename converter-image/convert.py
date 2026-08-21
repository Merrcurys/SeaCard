import logging
import sys
from pathlib import Path

from PIL import Image

for _stream in (sys.stdout, sys.stderr):
    if _stream is not None:
        _reconfigure = getattr(_stream, "reconfigure", None)
        if _reconfigure is not None:
            try:
                _reconfigure(encoding="utf-8")
            except Exception:
                pass

INPUT_DIR = Path(__file__).parent / "input"
OUTPUT_DIR = Path(__file__).parent / "output"

WEBP_QUALITY = 90
MAX_LONG_SIDE_PX = 600
ASPECT_RATIO = 1.574

SUPPORTED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
)
log = logging.getLogger("webp-converter")


def crop_to_aspect_ratio(image: Image.Image, aspect_ratio: float) -> Image.Image:
    width, height = image.size
    current_ratio = width / height

    if abs(current_ratio - aspect_ratio) < 1e-3:
        return image

    if current_ratio > aspect_ratio:
        new_width = round(height * aspect_ratio)
        left = (width - new_width) // 2
        box = (left, 0, left + new_width, height)
    else:
        new_height = round(width / aspect_ratio)
        top = (height - new_height) // 2
        box = (0, top, width, top + new_height)

    log.info("    Кадрирование %dx%d -> %dx%d", width, height, box[2] - box[0], box[3] - box[1])
    return image.crop(box)


def resize_long_side(image: Image.Image, max_long_side: int) -> Image.Image:
    width, height = image.size
    long_side = max(width, height)

    if long_side <= max_long_side:
        log.info("    Пропуск масштабирования: %dx%d уже <= %d px", width, height, max_long_side)
        return image

    scale = max_long_side / long_side
    new_size = (round(width * scale), round(height * scale))
    log.info("    Масштабирование %dx%d -> %dx%d", width, height, new_size[0], new_size[1])
    return image.resize(new_size, Image.Resampling.LANCZOS)


def process_file(input_path: Path) -> None:
    log.info("Обработка: %s", input_path.name)

    try:
        with Image.open(input_path) as image:
            image = image.convert("RGB")
            image = crop_to_aspect_ratio(image, ASPECT_RATIO)
            image = resize_long_side(image, MAX_LONG_SIDE_PX)

            output_path = OUTPUT_DIR / (input_path.stem + ".webp")
            image.save(output_path, "WEBP", quality=WEBP_QUALITY)
            log.info("    Сохранено: %s (%dx%d, качество %d)", output_path.name, image.width, image.height, WEBP_QUALITY)
    except Exception as exc:
        log.error("    Ошибка при обработке %s: %s", input_path.name, exc)


def main() -> None:
    INPUT_DIR.mkdir(parents=True, exist_ok=True)
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    files = sorted(
        p for p in INPUT_DIR.iterdir()
        if p.is_file() and p.suffix.lower() in SUPPORTED_EXTENSIONS
    )

    if not files:
        log.warning("В папке input нет изображений (%s)", ", ".join(sorted(SUPPORTED_EXTENSIONS)))
        return

    log.info("Найдено изображений: %d", len(files))

    for file in files:
        process_file(file)

    log.info("Готово. Обработано файлов: %d", len(files))


if __name__ == "__main__":
    main()
