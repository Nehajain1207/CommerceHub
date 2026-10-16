# Builds the React app, then serves it with nginx. The same nginx also acts as the load
# balancer: /api requests are shared between all running copies of the back end.
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# 1.27.3 or newer is needed for "resolve" in nginx.conf
FROM nginx:1.27.5-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
