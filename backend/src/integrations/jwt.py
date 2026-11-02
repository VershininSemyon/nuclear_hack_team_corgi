
from datetime import datetime, timedelta, timezone

import jwt

from config.settings import settings
from exceptions.auth import InvalidTokenError, TokenExpiredError


def create_access_token(data: dict) -> str:
    payload = {
        **data,
        "token_type": "access",
        "exp": datetime.now(timezone.utc) + timedelta(minutes=settings.ACCESS_TOKEN_LIFETIME_MINUTES)
    }

    return jwt.encode(
        payload=payload,
        algorithm=settings.JWT_ALGORITHM,
        key=settings.JWT_SIGNING_KEY
    )


def create_refresh_token(data: dict) -> str:
    payload = {
        **data,
        "token_type": "refresh",
        "exp": datetime.now(timezone.utc) + timedelta(days=settings.REFRESH_TOKEN_LIFETIME_DAYS)
    }

    return jwt.encode(
        payload=payload,
        algorithm=settings.JWT_ALGORITHM,
        key=settings.JWT_SIGNING_KEY
    )


def decode_token(token: str) -> dict:
    try:
        return jwt.decode(
            jwt=token,
            algorithms=[settings.JWT_ALGORITHM],
            key=settings.JWT_SIGNING_KEY
        )
    except jwt.ExpiredSignatureError:
        raise TokenExpiredError()
    except jwt.InvalidTokenError:
        raise InvalidTokenError()
