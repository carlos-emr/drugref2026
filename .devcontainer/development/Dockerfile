FROM tomcat:11.0-jdk21-temurin

# Set environment variables
ENV CATALINA_HOME=/usr/local/tomcat
ENV PATH=$CATALINA_HOME/bin:$PATH
ENV CATALINA_OPTS="--add-opens java.base/java.net=ALL-UNNAMED -Djava.awt.headless=true -Ddrugref_override_properties=/root/drugref2.properties"
ENV JPDA_OPTS="-agentlib:jdwp=transport=dt_socket,address=8000,server=y,suspend=n"
ENV JPDA_ADDRESS=8000
ENV JPDA_TRANSPORT=dt_socket

LABEL author="OpenOSP" \
      version="1.0.0" \
      description="Drugref 2026 Docker Image for Development Environment"

WORKDIR /workspace

# Install required packages
RUN apt-get update && apt-get install -y --no-install-recommends \
    dos2unix curl git wget ca-certificates gnupg \
    locales iputils-ping \
    maven mariadb-client \
    nano vim-tiny \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# Install Node.js (latest LTS)
RUN curl -fsSL https://deb.nodesource.com/setup_lts.x | bash - \
    && apt-get install -y nodejs \
    && node --version && npm --version

# Install GitHub CLI
RUN curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg | dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg \
    && chmod go+r /usr/share/keyrings/githubcli-archive-keyring.gpg \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" | tee /etc/apt/sources.list.d/github-cli.list > /dev/null \
    && apt-get update \
    && apt-get install -y gh \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# Install Claude Code CLI
RUN curl -fsSL https://claude.ai/install.sh | bash
ENV PATH="${PATH}:/root/.local/bin"

# Remove unnecessary default Tomcat webapps
RUN rm -rf $CATALINA_HOME/webapps/ROOT/* \
    $CATALINA_HOME/webapps/docs \
    $CATALINA_HOME/webapps/examples \
    $CATALINA_HOME/webapps/host-manager \
    $CATALINA_HOME/webapps/manager

# Copy scripts
COPY ./scripts /scripts
RUN dos2unix /scripts/* && chmod +x /scripts/*
ENV PATH="/scripts:${PATH}"

COPY ./scripts/startup.sh /startup.sh
RUN dos2unix /startup.sh && chmod +x /startup.sh

# Copy drugref2.properties (can be overridden by volume mount)
COPY ./config/drugref2.properties /root/drugref2.properties

# Set up Maven repository directory
RUN mkdir -p /root/.m2 \
    && chown -R root:root /root/.m2 \
    && chmod -R 777 /root/.m2

EXPOSE 8080 8000

CMD ["/startup.sh"]
