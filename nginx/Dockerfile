# Nginx de CoreTech: compila la SPA con Node y la sirve con la configuración de HTTPS.
# El contexto de construcción es prototipo/ (docker-compose.prod.yml).
FROM node:22-alpine AS compilar
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx/coretech.conf /etc/nginx/conf.d/default.conf
COPY nginx/encabezados-seguridad.conf /etc/nginx/snippets/encabezados-seguridad.conf
COPY --from=compilar /app/dist /usr/share/nginx/html
EXPOSE 80 443
