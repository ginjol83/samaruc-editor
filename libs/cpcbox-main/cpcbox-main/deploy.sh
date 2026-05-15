#!/bin/sh

# CPCBox Deployment Script
# Uploads all project files to the web server

echo "Starting CPCBox deployment..."

# Check if rsync is available
if ! command -v rsync &> /dev/null; then
    echo "Error: rsync is not installed or not in PATH"
    exit 1
fi

# Check if we can connect to the server
echo "Testing connection to server..."
if ! ssh -o ConnectTimeout=10 -o BatchMode=yes amstrad@server exit 2>/dev/null; then
    echo "Error: Cannot connect to server 'amstrad@server'"
    echo "Please check:"
    echo "1. SSH key is set up correctly"
    echo "2. Server hostname 'server' is resolvable"
    echo "3. User 'amstrad' exists on the server"
    exit 1
fi

# Perform the rsync upload with verbose output
echo "Uploading files to server..."
rsync -avz --progress --exclude='.git' --exclude='*.md' --exclude='deploy.sh' * amstrad@server:/home/amstrad/htdocs/amstrad.retrogamecoders.com/

if [ $? -eq 0 ]; then
    echo "Deployment completed successfully!"
    echo "CPCBox is now live at: http://amstrad.retrogamecoders.com"
else
    echo "Deployment failed with exit code $?"
    exit 1
fi
