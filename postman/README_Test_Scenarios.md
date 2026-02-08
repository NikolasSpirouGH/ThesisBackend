# Groups & Pipeline Copy - Test Scenarios

## Quick Setup

1. **Start the backend application**
2. **Run Flyway migrations** (V5 adds demo data)
3. **Import Postman collection**: `Groups_and_Pipeline_Copy_Collection.json`

---

## Test Users

| Username    | Password       | Role  | Description              |
|-------------|----------------|-------|--------------------------|
| bigspy      | adminPassword  | ADMIN | Admin user               |
| johnken     | adminPassword  | ADMIN | Admin user               |
| nickriz     | userPassword   | USER  | ML Research Team leader  |
| maria_ds    | testPassword   | USER  | Data Science Lab leader  |
| alex_ml     | testPassword   | USER  | ML Engineer              |
| elena_ai    | testPassword   | USER  | AI Researcher            |
| george_dev  | testPassword   | USER  | Software Developer       |
| sophia_fe   | testPassword   | USER  | Frontend Developer       |

---

## Demo Groups (Pre-created by V5 migration)

| ID | Name                | Leader   | Members                         |
|----|---------------------|----------|----------------------------------|
| 1  | ML Research Team    | nickriz  | maria_ds, alex_ml, elena_ai     |
| 2  | AI Development Team | bigspy   | nickriz, george_dev, sophia_fe  |
| 3  | Data Science Lab    | maria_ds | alex_ml, elena_ai, nickriz      |

---

## API Endpoints

### Groups API (`/api/groups`)

| Method | Endpoint                  | Description                    | Auth Required |
|--------|---------------------------|--------------------------------|---------------|
| POST   | `/`                       | Create a new group             | Yes           |
| GET    | `/my-groups`              | Get all groups I'm part of     | Yes           |
| GET    | `/leading`                | Get groups I lead              | Yes           |
| GET    | `/member-of`              | Get groups I'm a member of     | Yes           |
| GET    | `/{groupId}`              | Get group details              | Yes           |
| POST   | `/{groupId}/members`      | Add members to group           | Leader/Admin  |
| DELETE | `/{groupId}/members`      | Remove members from group      | Leader/Admin  |
| DELETE | `/{groupId}`              | Delete a group                 | Leader/Admin  |

### Dataset Group Sharing (`/api/datasets`)

| Method | Endpoint                           | Description                    | Auth Required |
|--------|------------------------------------|--------------------------------|---------------|
| POST   | `/{datasetId}/share-group`         | Share dataset with a group     | Owner         |
| DELETE | `/{datasetId}/share-group/{groupId}` | Unshare from group           | Owner/Admin   |

### Model Group Sharing (`/api/models`)

| Method | Endpoint                         | Description                    | Auth Required |
|--------|----------------------------------|--------------------------------|---------------|
| POST   | `/{modelId}/share-group`         | Share model with a group       | Owner         |
| DELETE | `/{modelId}/share-group/{groupId}` | Unshare from group           | Owner/Admin   |

### Pipeline Copy (`/api/pipeline`)

| Method | Endpoint                              | Description                          | Auth Required |
|--------|---------------------------------------|--------------------------------------|---------------|
| POST   | `/{trainingId}/copy`                  | Copy pipeline to user or group       | Owner/Shared  |
| POST   | `/{trainingId}/copy-to-group/{groupId}` | Copy to all group members          | Owner/Shared  |
| GET    | `/copy/{pipelineCopyId}`              | Get copy details with provenance     | Yes           |
| GET    | `/copies/sent`                        | Get copies I initiated               | Yes           |
| GET    | `/copies/received`                    | Get copies I received                | Yes           |

---

## Test Scenarios

### Scenario 1: Team Collaboration
**Goal**: Team leader shares resources with their team

1. Login as `nickriz` (ML Research Team leader)
2. View my teams (`GET /groups/my-groups`)
3. Share dataset with team (`POST /datasets/1/share-group`)
4. Copy training pipeline to all team members (`POST /pipeline/1/copy-to-group/1`)

### Scenario 2: New Team Member Onboarding
**Goal**: Admin onboards a new team member with starter resources

1. Login as `bigspy` (Admin)
2. View AI Development Team (`GET /groups/2`)
3. Add elena_ai to team (`POST /groups/2/members`)
4. Copy starter pipeline to new member (`POST /pipeline/1/copy`)

### Scenario 3: Cross-Team Sharing
**Goal**: Share a model with multiple teams

1. Login as `maria_ds` (Data Science Lab leader)
2. Share model with ML Research Team (`POST /models/1/share-group`)
3. Share same model with AI Development Team (`POST /models/1/share-group`)

### Scenario 4: Pipeline Copy with Provenance
**Goal**: Copy a pipeline and verify provenance tracking

1. Login as dataset/training owner
2. Copy pipeline (`POST /pipeline/1/copy`)
3. Get copy details (`GET /pipeline/copy/{id}`)
4. Verify mappings show: old dataset ID → new dataset ID, old model ID → new model ID

---

## Request/Response Examples

### Create Group
```json
// POST /api/groups
{
    "name": "New Project Team",
    "description": "A new team for our exciting project",
    "initialMemberUsernames": ["alex_ml", "elena_ai"]
}

// Response
{
    "id": 4,
    "name": "New Project Team",
    "description": "A new team for our exciting project",
    "leaderUsername": "nickriz",
    "memberUsernames": ["alex_ml", "elena_ai"],
    "memberCount": 2,
    "createdAt": "2024-02-07T12:00:00Z"
}
```

### Share Dataset with Group
```json
// POST /api/datasets/1/share-group
{
    "groupId": 1,
    "comment": "Dataset for Q1 analysis"
}
```

### Copy Pipeline
```json
// POST /api/pipeline/1/copy
{
    "targetUsername": "alex_ml"
}

// Response
{
    "pipelineCopyId": 1,
    "sourceTrainingId": 1,
    "targetTrainingId": 5,
    "copiedByUsername": "nickriz",
    "copyForUsername": "alex_ml",
    "status": "COMPLETED",
    "copyDate": "2024-02-07T12:00:00Z",
    "mappings": {
        "DATASET": {
            "sourceId": 1,
            "targetId": 10,
            "minioSourceKey": "datasets/nickriz/data.csv",
            "minioTargetKey": "datasets/alex_ml/COPY_1707307200_data.csv",
            "status": "COPIED"
        },
        "MODEL": {
            "sourceId": 1,
            "targetId": 8,
            "minioSourceKey": "models/model_1.pkl",
            "minioTargetKey": "models/model_8.pkl",
            "status": "COPIED"
        }
    }
}
```

---

## Error Codes

| Code | Scenario                                      |
|------|-----------------------------------------------|
| 401  | Missing or invalid JWT token                  |
| 403  | Not authorized (not owner, not leader, etc.)  |
| 404  | Group, dataset, model, or training not found  |
| 400  | Already shared with group, duplicate name     |
| 500  | Internal error (MinIO failure, etc.)          |

---

## Notes

- All endpoints require authentication via JWT Bearer token
- Group leaders and ADMINs can manage group membership
- Only resource owners can share to groups
- Pipeline copy creates complete duplicates with new IDs
- Provenance mappings track original → copy relationships
- MinIO server-side copy is used for efficiency
