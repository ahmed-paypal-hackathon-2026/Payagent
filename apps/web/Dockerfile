# syntax=docker/dockerfile:1

FROM node:20.18.0-alpine AS deps
WORKDIR /app
COPY apps/web/package.json apps/web/package-lock.json ./
RUN npm ci

FROM node:20.18.0-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY apps/web/ .
ARG NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
ARG NEXT_PUBLIC_USE_MOCKS=true
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL
ENV NEXT_PUBLIC_USE_MOCKS=$NEXT_PUBLIC_USE_MOCKS
RUN npm run build

FROM node:20.18.0-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
RUN adduser -D -u 10001 nextjs
USER nextjs
COPY --from=build /app/public ./public
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
EXPOSE 3000
ENV PORT=3000
CMD ["node", "server.js"]
