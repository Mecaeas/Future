# Configuração do MySQL

O projeto usa Spring Data JPA para salvar usuários, eventos e participações. Para
executar com MySQL:

1. Inicie o servidor MySQL e crie o banco:

   ```sql
   CREATE DATABASE demo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

2. No PowerShell, defina o usuário e a senha usados no seu MySQL:

   ```powershell
   $env:DB_USERNAME = "root"
   $env:DB_PASSWORD = "sua_senha"
   ```

   `DB_HOST`, `DB_PORT` e `DB_NAME` são opcionais e, por padrão, usam `localhost`,
   `3306` e `demo`. Não coloque senhas reais no código.

3. Na pasta `demo` (onde está o `pom.xml`), inicie a aplicação:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

O Hibernate cria/atualiza as tabelas automaticamente (`ddl-auto=update`). Os testes
usam H2 em memória e não precisam de um servidor MySQL. As imagens dos eventos
continuam em `uploads/eventos`; o banco salva os caminhos, não os arquivos.
