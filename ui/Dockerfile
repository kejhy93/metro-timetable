FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY composeApp/build/dist/wasmJs/productionExecutable /usr/share/nginx/html
