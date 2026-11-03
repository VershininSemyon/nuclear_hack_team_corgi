
from src.db.unitofwork import UnitOfWork
from src.exceptions.auth import InvalidPasswordError, InvalidTokenTypeError, UserNotFoundError
from src.integrations.hashing import verify_password
from src.integrations.jwt import create_access_token, create_refresh_token, decode_token
from src.schemas.auth import JWTTokenPairResponseSchema, UserLoginSchema
from src.schemas.user import UserReadSchema


class AuthService:
    def __init__(self, uow: UnitOfWork):
        self.uow = uow

    async def get_tokens(self, login_data: UserLoginSchema) -> JWTTokenPairResponseSchema:
        async with self.uow:
            user = await self.uow.user_repository.get_by_username(login_data.username)

        if not user:
            raise UserNotFoundError()

        if not verify_password(login_data.password, user.password):
            raise InvalidPasswordError()

        user_data = {
            "id": str(user.id),
            "username": user.username,
            "email": user.email
        }

        return JWTTokenPairResponseSchema.model_validate({
            "access": create_access_token(user_data),
            "refresh": create_refresh_token(user_data)
        })

    def refresh_token(self, refresh_token: str) -> str:
        data = decode_token(refresh_token)

        if data.get('token_type') != 'refresh':
            raise InvalidTokenTypeError()

        user_data = {
            "id": data['id'],
            "username": data['username'],
            "email": data['email']
        }

        access = create_access_token(user_data)
        return access

    async def authenticate_user(self, access_token: str) -> UserReadSchema:
        data = decode_token(access_token)

        if data.get('token_type') != 'access':
            raise InvalidTokenTypeError()

        async with self.uow:
            user = await self.uow.user_repository.get_by_id(data['id'])

            if user is None:
                raise UserNotFoundError()

            return UserReadSchema.model_validate(user)
