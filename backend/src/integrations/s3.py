
import aioboto3
from botocore.exceptions import ClientError
from src.config.settings import settings


class S3Service:
    def __init__(self) -> None:
        self.session = aioboto3.Session()
        self.bucket = settings.MINIO_BUCKET
        self._client_kwargs = {
            "service_name": "s3",
            "endpoint_url": f"http://{settings.MINIO_ENDPOINT}",
            "aws_access_key_id": settings.MINIO_ROOT_USER,
            "aws_secret_access_key": settings.MINIO_ROOT_PASSWORD,
            "region_name": settings.MINIO_REGION,
        }
        self._bucket_ensured = False

    async def ensure_bucket(self) -> None:
        if self._bucket_ensured:
            return
        async with self.session.client(**self._client_kwargs) as client:
            try:
                await client.head_bucket(Bucket=self.bucket)
            except ClientError:
                await client.create_bucket(Bucket=self.bucket)
        self._bucket_ensured = True

    async def upload_file(
        self,
        key: str,
        data: bytes,
        content_type: str = "audio/mpeg",
    ) -> str:
        await self.ensure_bucket()
        async with self.session.client(**self._client_kwargs) as client:
            await client.put_object(
                Bucket=self.bucket,
                Key=key,
                Body=data,
                ContentType=content_type,
            )
        return key

    async def download_file(self, key: str) -> bytes:
        async with self.session.client(**self._client_kwargs) as client:
            response = await client.get_object(Bucket=self.bucket, Key=key)
            async with response["Body"] as stream:
                return await stream.read()

    async def delete_file(self, key: str) -> None:
        async with self.session.client(**self._client_kwargs) as client:
            await client.delete_object(Bucket=self.bucket, Key=key)


s3_service = S3Service()
