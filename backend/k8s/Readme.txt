Client (Browser / Mobile)
        │
        ▼
   Ingress Controller (NGINX, Traefik…)
        │
        ▼
   Service (ClusterIP → smartchat-backend:80)
        │
        ▼
   Pod(s) (Deployment → port 8080, Spring Boot app)



kubectl apply -f k8s/deployment.yml
kubectl apply -f k8s/service.yml
kubectl apply -f k8s/ingress.yml

On cloud providers (EKS, GKE, AKS), Ingress requires an ingress controller installed (e.g., NGINX Ingress via Helm).
Change host: smartchat.local to your actual domain.