FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
# Install gettext for envsubst
RUN apk add --no-cache gettext
COPY nginx.conf /etc/nginx/conf.d/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
# Use envsubst to replace environment variables in nginx config at runtime
# Provide default BACKEND_URL if not set
CMD export BACKEND_URL="${BACKEND_URL:-https://customer-retention-platform.onrender.com}" && envsubst '${BACKEND_URL}' < /etc/nginx/conf.d/default.conf.template > /etc/nginx/conf.d/default.conf && nginx -g 'daemon off;'
