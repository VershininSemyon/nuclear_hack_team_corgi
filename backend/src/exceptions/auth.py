
from exceptions.base import AppError


class AuthError(AppError):
    status_code: int = 401
    detail: str = "Ошибка аутентификации"


class TokenDecodeError(AuthError):
    status_code: int = 401
    detail: str = "Ошибка декодирования токена"


class TokenExpiredError(TokenDecodeError):
    status_code: int = 401
    detail: str = "Токен просрочен"


class InvalidTokenError(TokenDecodeError):
    status_code: int = 401
    detail: str = "Недействительный токен"


class InvalidUserDataError(AuthError):
    status_code: int = 401
    detail: str = "Неверные учётные данные"


class UserNotFoundError(InvalidUserDataError):
    status_code: int = 401
    detail: str = "Пользователь не найден"


class InvalidPasswordError(InvalidUserDataError):
    status_code: int = 401
    detail: str = "Неверный пароль"


class InvalidTokenTypeError(AuthError):
    status_code: int = 401
    detail: str = "Неверный тип токена"
