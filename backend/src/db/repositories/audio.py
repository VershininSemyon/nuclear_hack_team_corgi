
import uuid
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from src.db.repositories.base import BaseRepository
from src.models.audio import AudioORM


class AudioRepository(BaseRepository[AudioORM]):
    def __init__(self, session: AsyncSession):
        super().__init__(AudioORM, session)

    async def get_by_user_id(self, user_id: uuid.UUID) -> list[AudioORM]:
        stmt = (
            select(AudioORM)
            .where(AudioORM.user_id == user_id)
            .order_by(AudioORM.created_at.desc())
        )
        result = await self.session.execute(stmt)
        return result.scalars().all()
