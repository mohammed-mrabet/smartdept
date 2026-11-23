# Jenkins prêt pour le pipeline SmartDept : JDK 21, client Docker, kubectl, Ansible et plugins nécessaires
FROM jenkins/jenkins:lts-jdk21
USER root
RUN apt-get update \
 && apt-get install -y --no-install-recommends docker.io ansible curl \
 && curl -fsSL -o /usr/local/bin/kubectl "https://dl.k8s.io/release/v1.31.2/bin/linux/amd64/kubectl" \
 && chmod +x /usr/local/bin/kubectl \
 && rm -rf /var/lib/apt/lists/*
USER jenkins
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt
