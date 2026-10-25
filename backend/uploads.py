"""Bounded multipart parsing that never spools recordings to disk."""

from fastapi import HTTPException, Request
from fastapi.routing import APIRoute
from starlette.formparsers import MultiPartException, MultiPartParser


MAX_AUDIO_BYTES = 20 * 1024 * 1024


class UploadTooLarge(MultiPartException):
    pass


class MemoryMultipartParser(MultiPartParser):
    # SpooledTemporaryFile uses zero to disable automatic disk rollover.
    spool_max_size = 0
    max_file_size = 0  # Compatibility with earlier Starlette releases.

    def on_part_begin(self) -> None:
        super().on_part_begin()
        self._audio_size = 0

    def on_part_data(self, data: bytes, start: int, end: int) -> None:
        if self._current_part.file is not None:
            self._audio_size += end - start
            if self._audio_size > MAX_AUDIO_BYTES:
                raise UploadTooLarge("audio file is too large")
        super().on_part_data(data, start, end)


class MemoryUploadRoute(APIRoute):
    def get_route_handler(self):
        handler = super().get_route_handler()

        async def memory_upload(request: Request):
            if request.headers.get("content-type", "").lower().startswith("multipart/form-data"):
                parser = MemoryMultipartParser(
                    request.headers, request.stream(), max_files=1, max_fields=8,
                    max_part_size=64 * 1024,
                )
                try:
                    form = await parser.parse()
                except UploadTooLarge as exc:
                    raise HTTPException(status_code=413, detail=exc.message) from exc
                except MultiPartException as exc:
                    raise HTTPException(status_code=400, detail=exc.message) from exc
                # Seed Starlette's form cache before FastAPI validates File/Form.
                request._form = form
                try:
                    return await handler(request)
                finally:
                    await form.close()
            return await handler(request)

        return memory_upload
