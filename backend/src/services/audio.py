
import base64
import logging
import uuid

from src.db.unitofwork import UnitOfWork
from src.exceptions.audio import AudioNotFoundError
from src.integrations.s3 import S3Service
from src.schemas.audio import (
    AudioCreateSchema,
    AudioReadSchema,
    AudioUpdateSchema,
    AudioWithDataSchema,
)
from src.services.validators import validate_audio_ownership

logger = logging.getLogger(__name__)


class AudioService:
    def __init__(self, uow: UnitOfWork, s3: S3Service):
        self.uow = uow
        self.s3 = s3

    async def create_audio(
        self,
        user_id: uuid.UUID,
        audio_data: AudioCreateSchema,
        file_data: bytes,
        content_type: str | None = None,
    ) -> AudioReadSchema:
        s3_key = f"{user_id}/{uuid.uuid4()}"

        await self.s3.upload_file(
            key=s3_key,
            data=file_data,
            content_type=content_type or "audio/mpeg",
        )

        async with self.uow:
            audio = await self.uow.audio_repository.create({
                "user_id": user_id,
                "s3_key": s3_key,
                **audio_data.model_dump(),
            })
            await self.uow.commit()

        return AudioReadSchema.model_validate(audio)

    async def get_user_audios(self, user_id: uuid.UUID) -> list[AudioReadSchema]:
        async with self.uow:
            audios = await self.uow.audio_repository.get_by_user_id(user_id)
        return [AudioReadSchema.model_validate(audio) for audio in audios]

    async def get_user_audios_with_data(
        self, user_id: uuid.UUID
    ) -> list[AudioWithDataSchema]:
        async with self.uow:
            audios = await self.uow.audio_repository.get_by_user_id(user_id)

        result: list[AudioWithDataSchema] = []
        for audio in audios:
            file_bytes = await self.s3.download_file(audio.s3_key)
            result.append(
                AudioWithDataSchema(
                    **AudioReadSchema.model_validate(audio).model_dump(),
                    audio_base64=base64.b64encode(file_bytes).decode("utf-8"),
                )
            )
        return result

    async def get_audio_with_data(
        self, user_id: uuid.UUID, audio_id: uuid.UUID
    ) -> AudioWithDataSchema:
        async with self.uow:
            audio = await self.uow.audio_repository.get_by_id(audio_id)

        if audio is None:
            raise AudioNotFoundError()

        validate_audio_ownership(user_id, audio.user_id)

        file_bytes = await self.s3.download_file(audio.s3_key)

        return AudioWithDataSchema(
            **AudioReadSchema.model_validate(audio).model_dump(),
            audio_base64=base64.b64encode(file_bytes).decode("utf-8"),
        )

    async def update_audio(
        self,
        user_id: uuid.UUID,
        audio_id: uuid.UUID,
        data: AudioUpdateSchema,
    ) -> AudioReadSchema:
        async with self.uow:
            audio = await self.uow.audio_repository.get_by_id(audio_id)

            if audio is None:
                raise AudioNotFoundError()

            validate_audio_ownership(user_id, audio.user_id)

            updated = await self.uow.audio_repository.update(audio_id, data.model_dump())
            await self.uow.commit()

        return AudioReadSchema.model_validate(updated)

    async def delete_audio(
        self,
        user_id: uuid.UUID,
        audio_id: uuid.UUID
    ) -> None:
        async with self.uow:
            audio = await self.uow.audio_repository.get_by_id(audio_id)

            if audio is None:
                raise AudioNotFoundError()

            validate_audio_ownership(user_id, audio.user_id)

            s3_key = audio.s3_key
            await self.uow.audio_repository.delete(audio_id)
            await self.uow.commit()

        try:
            await self.s3.delete_file(s3_key)
        except Exception:
            logger.exception(f"Не удалось удалить объект из S3: {s3_key}")
