
from exceptions.base import AppError


class UserError(AppError):
    status_code: int = 400
    detail: str = "Ошибка пользователя"


class UsernameAlreadyExistsError(UserError):
    status_code: int = 400
    detail: str = "Пользователь с таким username уже существует"


class EmailAlreadyExistsError(UserError):
    status_code: int = 400
    detail: str = "Пользователь с таким email уже существует"
