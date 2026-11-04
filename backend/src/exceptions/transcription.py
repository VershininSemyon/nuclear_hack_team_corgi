
from src.exceptions.base import AppError


class TranscriptionError(AppError):
    status_code: int = 400
    detail: str = "Ошибка транскрипции"


class TranscriptionNotFoundError(TranscriptionError):
    status_code: int = 404
    detail: str = "Транскрипция не найдена"


class TranscriptionAudioMismatchError(TranscriptionError):
    status_code: int = 404
    detail: str = "Транскрипция не принадлежит этому аудио"
