### Keycloak

терминал в папке `keycloak`:

    cd keycloak
    docker compose up -d

### Проверка

http://localhost:8180 - страница Keycloak.

- Username: admin
- Password: admin

### Проверка realm

1. В левом верхнем углу выбрать realm **smartcast**.
2. Clients → должен быть **smartcast-client**.
3. Realm roles → должны быть **LISTENER, AUTHOR, MODERATOR, ADMIN**.
4. Users → должен быть **testuser**.


### Проверка авторизации

    http://localhost:8080/rest-api/author/hello

- Username: testuser
- Password: testpass

## Если что-то не работает попробуйте очистить куки

