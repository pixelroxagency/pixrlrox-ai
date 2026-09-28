import re

def get_implemented_routes():
    with open('app/src/main/java/com/example/ui/navigation/AppNavHost.kt', 'r') as f:
        content = f.read()
    routes = set(re.findall(r'composable\(Screen\.([A-Za-z0-9.]+)\.route\)', content))
    return {f"Screen.{r}.route" for r in routes}

def analyze():
    implemented_routes = get_implemented_routes()
    with open('app/src/main/java/com/example/ui/screens/tools/ToolCatalog.kt', 'r') as f:
        content = f.read()
    
    # Match ToolItem entries: ToolItem("id", "title", "description", icon, route, "categoryId")
    # This regex handles the route part carefully
    tool_pattern = re.compile(r'ToolItem\("([^"]+)", "([^"]+)", "([^"]+)", [^,]+, ([^,]+), "([^"]+)"\)')
    
    tools = []
    for match in tool_pattern.finditer(content):
        tid, title, desc, route, cat = match.groups()
        route = route.strip()
        status = "COMING_SOON"
        if route != "null" and route in implemented_routes:
            status = "IMPLEMENTED"
        
        tools.append({
            'id': tid,
            'title': title,
            'category': cat,
            'route': route,
            'status': status
        })
    
    # Output rows
    for t in tools:
        print(f"TOOL|{t['id']}|{t['title']}|{t['category']}|{t['route']}|{t['status']}")

analyze()
