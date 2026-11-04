
import uuid
from datetime import datetime

import pydantic
from pydantic import NonNegativeInt, model_validator


class TimestampSchema(pydantic.BaseModel):
    hour: NonNegativeInt
    minute: NonNegativeInt
    second: NonNegativeInt
    millisecond: NonNegativeInt


class WordsDataSchema(pydantic.BaseModel):
    word: str
    speaker: str
    start_time: TimestampSchema
    end_time: TimestampSchema

    # TODO: написать валидацию, что end_time > start_time


class TranscriptionCreateSchema(pydantic.BaseModel):
    text: str
    speaker_count: int
    word_count: int
    phrase_count: int
    words_data: list[WordsDataSchema]


class TranscriptionReadSchema(TranscriptionCreateSchema):
    id: uuid.UUID
    audio_id: uuid.UUID

    text: str
    speaker_count: int
    word_count: int
    phrase_count: int
    words_data: list[WordsDataSchema]

    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True


class TranscriptionUpdateSchema(pydantic.BaseModel):
    text: str
    speaker_count: int
    word_count: int
    phrase_count: int
    words_data: list[WordsDataSchema]
