# LocalStack PRO Setup Guide for AWS Cognito Testing

## Overview

LocalStack PRO provides full AWS Cognito support for local testing. This guide explains how to set up and use LocalStack PRO with the AWS Cognito adapter tests.

---

## Prerequisites

1. **Docker Desktop** - Running on your machine
2. **LocalStack PRO license key** - Get it from https://localstack.cloud
3. **Java 21+** and **Maven 3.9+**

---

## Step 1: Get LocalStack PRO License

### Option A: Free Trial (14 days)
1. Visit https://app.localstack.cloud/sign-up
2. Sign up for a free trial
3. Get your API key from https://app.localstack.cloud/workspace/auth-tokens

### Option B: Purchase License
1. Visit https://localstack.cloud/pricing
2. Choose a plan (Team, Business, or Enterprise)
3. Get your API key after purchase

---

## Step 2: Configure LocalStack PRO Auth Token

### Method 1: Environment Variable (Recommended for CI/CD)

```bash
# Add to your ~/.zshrc or ~/.bashrc
export LOCALSTACK_AUTH_TOKEN="your-auth-token-here"

# Reload shell
source ~/.zshrc
```

### Method 2: LocalStack Configuration File

Create `~/.localstack/config`:

```bash
mkdir -p ~/.localstack
cat > ~/.localstack/config << EOF
auth_token=your-auth-token-here
EOF
```

### Method 3: Docker Environment Variable

Set the auth token when running LocalStack:

```bash
docker run -e LOCALSTACK_AUTH_TOKEN=your-auth-token-here localstack/localstack-pro
```

---

## Step 3: Configure Maven for Integration Tests

The `pom.xml` is already configured to use LocalStack PRO. Verify the configuration:

```xml
<!-- In pom.xml - already configured -->
<dependencies>
    <!-- LocalStack for AWS emulation -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>localstack</artifactId>
        <version>1.19.3</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## Step 4: Update Test to Use LocalStack PRO

The test is already configured. Just **remove or comment out the @Disabled annotation**:

**File**: `src/test/java/com/firefly/idp/cognito/adapter/CognitoIdpAdapterLocalStackIT.java`

```java
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("integration")
// @Disabled("Remove this line to enable LocalStack PRO tests")  // <-- Comment this out
class CognitoIdpAdapterLocalStackIT {
```

### Update LocalStack Container to Use PRO Image

```java
@Container
static LocalStackContainer localstack = new LocalStackContainer(
        DockerImageName.parse("localstack/localstack-pro:latest"))  // Use PRO image
        .withServices(LocalStackContainer.Service.S3)
        .withEnv("LOCALSTACK_AUTH_TOKEN", System.getenv("LOCALSTACK_AUTH_TOKEN"));
```

---

## Step 5: Run Integration Tests

### Run with Maven

```bash
# Set auth token (if not in config file)
export LOCALSTACK_AUTH_TOKEN="your-auth-token-here"

# Run integration tests
cd ~/Development/firefly/lib-idp-aws-cognito-impl
mvn clean verify -Dit.test=CognitoIdpAdapterLocalStackIT

# Or run all tests including integration
mvn clean verify
```

### Run with IntelliJ IDEA

1. **Set Environment Variable**:
   - Run → Edit Configurations
   - Select the test
   - Add Environment Variable: `LOCALSTACK_AUTH_TOKEN=your-token`

2. **Run the test** as normal

### Run with Docker Compose (Alternative)

Create `docker-compose.yml`:

```yaml
version: '3.8'

services:
  localstack-pro:
    image: localstack/localstack-pro:latest
    ports:
      - "4566:4566"
    environment:
      - LOCALSTACK_AUTH_TOKEN=${LOCALSTACK_AUTH_TOKEN}
      - SERVICES=cognito-idp
      - DEBUG=1
      - PERSISTENCE=1
    volumes:
      - ./localstack-data:/var/lib/localstack
      - /var/run/docker.sock:/var/run/docker.sock
```

Start LocalStack PRO:

```bash
export LOCALSTACK_AUTH_TOKEN="your-auth-token-here"
docker-compose up -d

# Run tests against running LocalStack
mvn test -Dit.test=CognitoIdpAdapterLocalStackIT
```

---

## Step 6: Verify LocalStack PRO is Working

### Test LocalStack PRO Connection

```bash
# Start LocalStack PRO
docker run -d \
  -p 4566:4566 \
  -e LOCALSTACK_AUTH_TOKEN=$LOCALSTACK_AUTH_TOKEN \
  localstack/localstack-pro:latest

# Wait a few seconds, then test
curl http://localhost:4566/_localstack/health

# Expected output should show cognito-idp as "running"
```

### Check Cognito Service

```bash
# Create a test user pool
aws --endpoint-url=http://localhost:4566 cognito-idp create-user-pool \
  --pool-name test-pool \
  --region us-east-1

# You should get a successful response with user pool details
```

---

## Troubleshooting

### Error: "API not yet implemented or pro feature (Status Code: 501)"

**Problem**: LocalStack Free version doesn't support Cognito

**Solution**: 
1. Verify you're using `localstack/localstack-pro` image (not `localstack/localstack`)
2. Verify `LOCALSTACK_AUTH_TOKEN` is set correctly
3. Check token is valid at https://app.localstack.cloud/workspace/auth-tokens

### Error: "Unable to pull localstack/localstack-pro"

**Problem**: Not authenticated to pull PRO image

**Solution**:
```bash
# Login to LocalStack docker registry
docker login -u localstack -p $LOCALSTACK_AUTH_TOKEN public.ecr.aws/localstack
```

### Error: "Connection refused to localhost:4566"

**Problem**: LocalStack container not running or port mapping issue

**Solution**:
```bash
# Check if container is running
docker ps | grep localstack

# Check logs
docker logs $(docker ps -q --filter ancestor=localstack/localstack-pro)

# Restart LocalStack
docker restart $(docker ps -q --filter ancestor=localstack/localstack-pro)
```

### Error: "Invalid auth token"

**Problem**: Auth token expired or invalid

**Solution**:
1. Login to https://app.localstack.cloud
2. Go to Auth Tokens section
3. Generate new token
4. Update `LOCALSTACK_AUTH_TOKEN` environment variable

---

## CI/CD Integration

### GitHub Actions

```yaml
name: Integration Tests

on: [push, pull_request]

jobs:
  integration-test:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Run Integration Tests
        env:
          LOCALSTACK_AUTH_TOKEN: ${{ secrets.LOCALSTACK_AUTH_TOKEN }}
        run: mvn clean verify
```

### GitLab CI

```yaml
integration-tests:
  stage: test
  image: maven:3.9-eclipse-temurin-21
  services:
    - docker:dind
  variables:
    LOCALSTACK_AUTH_TOKEN: $LOCALSTACK_AUTH_TOKEN
    DOCKER_HOST: tcp://docker:2375
  script:
    - mvn clean verify
  only:
    - main
    - develop
```

---

## Cost Considerations

### LocalStack PRO Pricing (as of 2024)

- **Free Trial**: 14 days, full features
- **Team Plan**: ~$25/month per user
- **Business Plan**: Contact sales
- **Enterprise Plan**: Custom pricing

### When to Use LocalStack PRO

✅ **Use LocalStack PRO when**:
- Running integration tests in CI/CD
- Need to test AWS Cognito locally
- Want faster test execution than mocking
- Need realistic AWS service behavior

❌ **Don't need LocalStack PRO when**:
- Unit tests with mocks are sufficient
- Testing against real AWS (dev account)
- Only testing basic functionality

---

## Alternative: Test Against Real AWS Cognito

If you don't want to use LocalStack PRO, you can test against real AWS:

### 1. Create Test User Pool

```bash
# Create user pool
aws cognito-idp create-user-pool \
  --pool-name firefly-test-pool \
  --region us-east-1

# Create app client
aws cognito-idp create-user-pool-client \
  --user-pool-id us-east-1_XXXXXXXXX \
  --client-name test-client \
  --explicit-auth-flows ALLOW_USER_PASSWORD_AUTH ALLOW_REFRESH_TOKEN_AUTH
```

### 2. Configure Test

```java
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Disabled("Enable only when testing against real AWS Cognito")
class CognitoIdpAdapterRealAwsIT {
    
    private static final String USER_POOL_ID = System.getenv("TEST_COGNITO_USER_POOL_ID");
    private static final String CLIENT_ID = System.getenv("TEST_COGNITO_CLIENT_ID");
    
    @BeforeAll
    void setUp() {
        // Use real AWS credentials and endpoints
        CognitoProperties properties = new CognitoProperties();
        properties.setRegion("us-east-1");
        properties.setUserPoolId(USER_POOL_ID);
        properties.setClientId(CLIENT_ID);
        
        // ... rest of setup
    }
}
```

---

## Best Practices

1. **Never commit auth tokens** to version control
2. **Use environment variables** for tokens
3. **Run integration tests in CI/CD** with LocalStack PRO
4. **Keep unit tests** (with mocks) for fast feedback
5. **Use integration tests** for critical flows only
6. **Clean up resources** after tests (user pools, users)

---

## Summary

**Quick Start**:

```bash
# 1. Get LocalStack PRO token
https://app.localstack.cloud/workspace/auth-tokens

# 2. Set environment variable
export LOCALSTACK_AUTH_TOKEN="your-token"

# 3. Run tests
cd ~/Development/firefly/lib-idp-aws-cognito-impl
mvn clean verify

# That's it! Tests will use LocalStack PRO automatically
```

---

## Support

- **LocalStack Docs**: https://docs.localstack.cloud
- **LocalStack PRO Features**: https://docs.localstack.cloud/references/coverage/
- **Community Slack**: https://localstack.cloud/slack
- **GitHub Issues**: https://github.com/localstack/localstack

---

## Next Steps

After configuring LocalStack PRO:

1. ✅ Enable the integration test (remove `@Disabled`)
2. ✅ Run `mvn clean verify`
3. ✅ See all Cognito operations tested end-to-end
4. ✅ Add more test scenarios as needed
5. ✅ Integrate into CI/CD pipeline
