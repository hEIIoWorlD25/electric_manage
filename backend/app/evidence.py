from io import BytesIO

from PIL import Image, ImageOps

MAX_PHOTO_BYTES = 2 * 1024 * 1024


def normalize_jpeg(data: bytes) -> bytes:
    """Decode only bounded JPEGs, correct orientation and omit camera metadata."""
    try:
        with Image.open(BytesIO(data), formats=['JPEG']) as original:
            if original.width * original.height > 12_000_000:
                raise ValueError('La foto es demasiado grande')
            image = ImageOps.exif_transpose(original).convert('RGB')
            image.thumbnail((1600, 1600))
            output = BytesIO()
            image.save(output, format='JPEG', quality=82)
    except (OSError, SyntaxError, Image.DecompressionBombError) as exc:
        raise ValueError('El archivo no es una fotografía JPEG válida') from exc
    jpeg = output.getvalue()
    if len(jpeg) > MAX_PHOTO_BYTES:
        raise ValueError('La foto no debe superar los 2 MB')
    return jpeg
