
import uuid

from src.exceptions.audio import AudioOwnershipError
from src.exceptions.transcription import TranscriptionAudioMismatchError


def validate_audio_ownership(
    user_id: uuid.UUID,
    audio_user_id: uuid.UUID
) -> None:
    if user_id != audio_user_id:
        raise AudioOwnershipError()


def validate_transcription_ownership(
    audio_id: uuid.UUID,
    transcription_audio_id: uuid.UUID
) -> None:

    if audio_id != transcription_audio_id:
        raise TranscriptionAudioMismatchError()
