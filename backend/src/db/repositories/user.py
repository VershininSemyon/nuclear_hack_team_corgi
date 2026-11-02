

from db.repositories.base import BaseRepository
from models.user import UserORM
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession


class UserRepository(BaseRepository[UserORM]):
    def __init__(self, session: AsyncSession):
        super().__init__(UserORM, session)

    async def get_by_username(self, username: str) -> UserORM:
        stmt = select(UserORM).where(UserORM.username == username)
        result = await self.session.execute(stmt)
        return result.scalars().first()

    async def get_by_email(self, email: str) -> UserORM:
        stmt = select(UserORM).where(UserORM.email == email)
        result = await self.session.execute(stmt)
        return result.scalars().first()
