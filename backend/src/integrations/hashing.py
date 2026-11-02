
import bcrypt

from config.settings import settings


def hash_password(password: str) -> str:
    prepared_password = f"{password}{settings.PASSWORD_SALT}".encode('utf-8')
    salt = bcrypt.gensalt()
    hashed = bcrypt.hashpw(prepared_password, salt)
    return hashed.decode('utf-8')


def verify_password(plain_password: str, hashed_password: str) -> bool:
    prepared_password = f"{plain_password}{settings.PASSWORD_SALT}".encode('utf-8')
    return bcrypt.checkpw(prepared_password, hashed_password.encode('utf-8'))
