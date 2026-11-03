
from typing import Generic, Type, TypeVar

from sqlalchemy import delete, insert, select, update
from sqlalchemy.ext.asyncio import AsyncSession

ModelType = TypeVar("ModelType")

class BaseRepository(Generic[ModelType]):
    def __init__(self, model: Type[ModelType], session: AsyncSession):
        self.model = model
        self.session = session

    async def get_all(self) -> list[ModelType]:
        stmt = select(self.model)
        result = await self.session.execute(stmt)
        return result.scalars().all()

    async def get_by_id(self, id) -> ModelType:
        stmt = select(self.model).where(self.model.id == id)
        result = await self.session.execute(stmt)
        return result.scalars().first()

    async def create(self, data: dict) -> ModelType:
        stmt = insert(self.model).values(**data).returning(self.model)
        result = await self.session.execute(stmt)
        return result.scalar_one_or_none()

    async def insert_many(self, data: list[dict]) -> None:
        stmt = insert(self.model).values(data)
        await self.session.execute(stmt)

    async def update(self, id, data: dict) -> ModelType:
        stmt = update(self.model).where(self.model.id == id).values(**data).returning(self.model)
        result = await self.session.execute(stmt)
        return result.scalar_one_or_none()

    async def delete(self, id) -> None:
        stmt = delete(self.model).where(self.model.id == id)
        await self.session.execute(stmt)
