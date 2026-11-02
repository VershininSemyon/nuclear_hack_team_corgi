
from fastapi import APIRouter, Cookie, HTTPException, Response, status

from api.dependencies import AuthServiceDep
from schemas.auth import JWTTokenPairResponseSchema, UserLoginSchema

auth_router = APIRouter(prefix="/auth", tags=["Auth"])


@auth_router.post(
    "/token",
    response_model=JWTTokenPairResponseSchema,
    status_code=status.HTTP_200_OK,
)
async def authenticate(
    login_data: UserLoginSchema,
    auth_service: AuthServiceDep,
    response: Response
):
    token_data = await auth_service.get_tokens(login_data)

    response.set_cookie(
        key="access_token",
        value=token_data.access,
        httponly=True,
        samesite="lax"
    )

    response.set_cookie(
        key="refresh_token",
        value=token_data.refresh,
        httponly=True,
        samesite="lax",
    )

    return token_data


@auth_router.post(
    "/token/refresh",
    status_code=status.HTTP_200_OK,
)
def refresh_token(
    auth_service: AuthServiceDep,
    response: Response,
    refresh_token: str | None = Cookie(default=None),
):
    if not refresh_token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Нет refresh токена"
        )

    access_token = auth_service.refresh_token(refresh_token)
    response.set_cookie(
        key="access_token",
        value=access_token,
        httponly=True,
        samesite="lax"
    )

    return {
        "access": access_token
    }


@auth_router.post(
    "/logout",
    status_code=status.HTTP_200_OK
)
def logout(response: Response):
    response.delete_cookie(key="access_token")
    response.delete_cookie(key="refresh_token")

    return {
        "success": True
    }
