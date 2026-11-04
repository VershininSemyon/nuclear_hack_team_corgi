
import uuid

from src.exceptions.audio import AudioOwnershipError


def validate_audio_ownership(user_id: uuid.UUID, audio_user_id: uuid.UUID) -> None:
    if user_id != audio_user_id:
        raise AudioOwnershipError()
