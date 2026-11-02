
from typing import Annotated

from fastapi import APIRouter, Response, status

from api.dependencies import CurrentUserDep, UserServiceDep
from schemas.user import UserCreateSchema, UserReadSchema, UserUpdateSchema

user_router = APIRouter(prefix="/users", tags=["Users"])

@user_router.post(
    "/",
    response_model=UserReadSchema,
    status_code=status.HTTP_201_CREATED
)
async def register_user(
    user_data: UserCreateSchema,
    user_service: UserServiceDep
) -> UserReadSchema:
    new_user = await user_service.create_user(user_data)
    return new_user


@user_router.get(
    "/me",
    response_model=UserReadSchema,
    status_code=status.HTTP_200_OK,
)
async def get_me(
    current_user: CurrentUserDep,
) -> UserReadSchema:
    return current_user


@user_router.delete(
    "/me",
    status_code=status.HTTP_204_NO_CONTENT,
)
async def delete_me(
    current_user: CurrentUserDep,
    user_service: UserServiceDep,
    response: Response
):
    await user_service.delete_user(current_user.id)
    response.delete_cookie(key="access_token")
    response.delete_cookie(key="refresh_token")


@user_router.put(
    "/me",
    response_model=UserReadSchema,
    status_code=status.HTTP_200_OK
)
async def change_me(
    user_data: UserUpdateSchema,
    current_user: CurrentUserDep,
    user_service: UserServiceDep,
) -> UserReadSchema:
    return await user_service.change_user(current_user, user_data)
