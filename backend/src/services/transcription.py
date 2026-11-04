
import uuid
from datetime import datetime, timezone

from src.db.unitofwork import UnitOfWork
from src.exceptions.audio import AudioNotFoundError
from src.exceptions.transcription import TranscriptionNotFoundError
from src.schemas.transcription import TranscriptionCreateSchema, TranscriptionReadSchema, TranscriptionUpdateSchema
from src.services.validators import validate_audio_ownership, validate_transcription_ownership


class TranscriptionService:
    def __init__(self, uow: UnitOfWork):
        self.uow = uow

    async def create_transcription(self, user_id: uuid.UUID, audio_id: uuid.UUID, transcription_data: TranscriptionCreateSchema) -> TranscriptionReadSchema:
        async with self.uow:
            audio = await self.uow.audio_repository.get_by_id(audio_id)
            if audio is None:
                raise AudioNotFoundError()
            validate_audio_ownership(user_id, audio.user_id)
            transcription = await self.uow.transcription_repository.create({
                "audio_id": audio_id, **transcription_data.model_dump()
            })
            await self.uow.commit()
        return TranscriptionReadSchema.model_validate(transcription)

    async def get_audio_transcriptions(self, user_id: uuid.UUID, audio_id: uuid.UUID) -> list[TranscriptionReadSchema]:
        async with self.uow:
            audio = await self.uow.audio_repository.get_by_id(audio_id)
            if audio is None:
                raise AudioNotFoundError()
            validate_audio_ownership(user_id, audio.user_id)
            transcriptions = await self.uow.transcription_repository.get_by_audio_id(audio_id)
        return [TranscriptionReadSchema.model_validate(t) for t in transcriptions]

    async def get_transcription(self, user_id: uuid.UUID, audio_id: uuid.UUID, transcription_id: uuid.UUID) -> TranscriptionReadSchema:
        async with self.uow:
            audio = await self.uow.audio_repository.get_by_id(audio_id)
            if audio is None:
                raise AudioNotFoundError()
            validate_audio_ownership(user_id, audio.user_id)
            transcription = await self.uow.transcription_repository.get_by_id(transcription_id)
            if transcription is None:
                raise TranscriptionNotFoundError()
            validate_transcription_ownership(audio_id, transcription.audio_id)
        return TranscriptionReadSchema.model_validate(transcription)

    async def delete_transcription(self, user_id: uuid.UUID, audio_id: uuid.UUID, transcription_id: uuid.UUID) -> None:
        async with self.uow:
            audio = await self.uow.audio_repository.get_by_id(audio_id)
            if audio is None:
                raise AudioNotFoundError()
            validate_audio_ownership(user_id, audio.user_id)
            transcription = await self.uow.transcription_repository.get_by_id(transcription_id)
            if transcription is None:
                raise TranscriptionNotFoundError()
            validate_transcription_ownership(audio_id, transcription.audio_id)
            
            # SOFT DELETE
            await self.uow.transcription_repository.update(transcription_id, {"deleted_at": datetime.now(timezone.utc)})
            await self.uow.commit()

    async def update_transcription(self, user_id: uuid.UUID, audio_id: uuid.UUID, transcription_id: uuid.UUID, transcription_data: TranscriptionUpdateSchema) -> TranscriptionReadSchema:
        async with self.uow:
            audio = await self.uow.audio_repository.get_by_id(audio_id)
            if audio is None:
                raise AudioNotFoundError()
            validate_audio_ownership(user_id, audio.user_id)
            transcription = await self.uow.transcription_repository.get_by_id(transcription_id)
            if transcription is None:
                raise TranscriptionNotFoundError()
            validate_transcription_ownership(audio_id, transcription.audio_id)
            updated = await self.uow.transcription_repository.update(transcription_id, transcription_data.model_dump())
            await self.uow.commit()
        return TranscriptionReadSchema.model_validate(updated)
