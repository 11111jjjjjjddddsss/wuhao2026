FROM golang:1.26.2-alpine AS build

WORKDIR /src

COPY server-go ./server-go

WORKDIR /src/server-go
RUN go mod download
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -o /out/nongji-server ./cmd/server

FROM alpine:3.21

WORKDIR /app

COPY --from=build /out/nongji-server /app/nongji-server
COPY server-go/assets /app/server-go/assets
COPY server-go/migrations /app/server-go/migrations

EXPOSE 3000

CMD ["/app/nongji-server"]
