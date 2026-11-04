
import uuid

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from src.db.repositories.base import BaseRepository
from src.models.transcription import TranscriptionORM


class TranscriptionRepository(BaseRepository[TranscriptionORM]):
    def __init__(self, session: AsyncSession):
        super().__init__(TranscriptionORM, session)

    async def get_by_audio_id(self, audio_id: uuid.UUID) -> list[TranscriptionORM]:
        stmt = select(TranscriptionORM).where(TranscriptionORM.audio_id == audio_id)
        result = await self.session.execute(stmt)
        return result.scalars().all()
