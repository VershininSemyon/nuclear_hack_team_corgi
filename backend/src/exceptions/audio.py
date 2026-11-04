
from src.exceptions.base import AppError


class AudioError(AppError):
    status_code: int = 400
    detail: str = "Ошибка аудио"


class AudioNotFoundError(AudioError):
    status_code: int = 404
    detail: str = "Аудио не найдено"


class AudioOwnershipError(AudioError):
    status_code: int = 403
    detail: str = "Вы не являетесь владельцем этого аудио"
