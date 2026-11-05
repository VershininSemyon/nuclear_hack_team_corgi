
import uuid
from datetime import datetime
import pydantic


class AudioCreateSchema(pydantic.BaseModel):
    title: str
    description: str | None = None


class AudioReadSchema(pydantic.BaseModel):
    id: uuid.UUID
    user_id: uuid.UUID
    title: str
    description: str | None
    s3_key: str
    created_at: datetime
    updated_at: datetime
    deleted_at: datetime | None = None

    class Config:
        from_attributes = True


class AudioWithDataSchema(AudioReadSchema):
    audio_base64: str | None = None
    content_type: str = "application/octet-stream"


class AudioUpdateSchema(pydantic.BaseModel):
    title: str
    description: str | None = None
