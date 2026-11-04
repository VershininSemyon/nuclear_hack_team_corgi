
import uuid
from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, Text, UniqueConstraint, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from src.db.database import Base


class AudioORM(Base):
    __tablename__ = "audios"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id"), nullable=False)
    title: Mapped[str] = mapped_column(Text, nullable=False)
    description: Mapped[str] = mapped_column(Text, nullable=True)
    s3_key: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())

    user: Mapped["UserORM"] = relationship("UserORM", back_populates="audios")
    transcriptions: Mapped[list["TranscriptionORM"]] = relationship("TranscriptionORM", back_populates="audio")

    __table_args__ = (
        UniqueConstraint("user_id", "s3_key", name="uq_user_s3_key"),
    )
