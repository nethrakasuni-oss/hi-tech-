# Hi-Tech LMS

Hi-Tech LMS is a Spring Boot + MySQL learning management system with a static HTML/CSS/JavaScript frontend. It includes authentication, course management, enrollments, attendance, exams, finance, support tickets, appointments, announcements, seed data, and an instructor-facing student performance prediction feature.

## Prerequisites

Install these before running the project:

- Java 17
- MySQL Server 8.x or compatible
- Python 3.10+ for the ML prediction feature
- VS Code Live Server, or any simple static file server for the `frontend` folder

The project includes Maven Wrapper, so Maven does not need to be installed separately.

## Project Structure

```text
frontend/                         Static frontend pages
src/main/java/com/hitech/lms/      Spring Boot backend
src/main/resources/                Application config and seed data
src/main/resources/seed/           JSON seed files loaded on backend startup
student_performance_predict.py     Python ML inference script
student_performance_model.pkl      Trained ML model
selected_features.pkl              Selected ML feature list
enrollment_status_encoder.pkl      Encoder for enrollment_status
target_label_encoder.pkl           Encoder for prediction labels
```

## Database Setup

Create or allow the app to create the database:

```sql
CREATE DATABASE hitech_lms;
```

Then check [application.properties](src/main/resources/application.properties):

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/hitech_lms?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:}
```

Set `DB_USERNAME` and `DB_PASSWORD` if your MySQL credentials are different from the defaults.

Hibernate is configured with:

```properties
spring.jpa.hibernate.ddl-auto=update
```

So tables are created/updated automatically during development.

## Python ML Setup

Install the Python packages used by the prediction script:

```powershell
pip install pandas scikit-learn joblib
```

Make sure these files exist in the project root:

```text
student_performance_predict.py
student_performance_model.pkl
selected_features.pkl
enrollment_status_encoder.pkl
target_label_encoder.pkl
```

The backend runs the prediction script using these settings in [application.properties](src/main/resources/application.properties):

```properties
ml.python-command=python
ml.project-dir=.
ml.predict-script=student_performance_predict.py
```

If you run the backend from an IDE and Python cannot find the model files, set an absolute path:

```properties
ml.project-dir=C:/Users/ahame/Documents/FreeLancing Project/hitech edu/hitech
```

## Run the Backend

From the project root:

```powershell
.\mvnw.cmd spring-boot:run
```

The backend runs at:

```text
http://localhost:8080
```

On startup, `DataInitializer` loads seed data from `src/main/resources/seed`. It creates users, programs, courses, enrollments, course content, exams, schedules, finance records, support data, announcements, and ML demo data for predictions.

## Run the Frontend

Open the `frontend` folder with VS Code Live Server, or serve it on port `5500`.

Expected frontend URL:

```text
http://localhost:5500/login.html
```

The backend CORS configuration already allows:

```text
http://localhost:5500
http://127.0.0.1:5500
http://localhost:3000
http://localhost:8080
```

## Seed Login Accounts

All seed users use the password shown below unless listed otherwise:

```text
Password2000.
```

Admin:

```text
Email: admin@hitech.com
Password: Admin2000.
```

Instructor examples:

```text
nimali.perera@hitech.lk
dinesh.fernando@hitech.lk
```

Student examples:

```text
kasun.jayasinghe@student.hitech.lk
ayesha.silva@student.hitech.lk
chamod.lakshan@student.hitech.lk
```

Support staff:

```text
farah.iqbal@hitech.lk
```

## Student Performance Prediction

Instructors and admins can use the prediction feature from:

```text
frontend/manage-courses.html
```

Click `Predict` next to a course. The backend endpoint is:

```http
GET /api/performance/courses/{courseId}/predictions
```

The current model uses these 10 features:

```text
enrollment_status
days_since_enrollment
attendance_percentage
attendance_absent_count
exams_attempted
exams_submitted
avg_exam_percentage
passed_exam_count
failed_exam_count
last_activity_days_ago
```

Predictions return categories such as:

```text
Good
Average
Medium
Low
```

## Retraining the ML Model

Use these files for training support:

```text
student-performance-ml-sample.csv
student-performance-ml-data-dictionary.csv
student_performance_colab_10_feature_training.py
```

The recommended retraining output files are:

```text
student_performance_model.pkl
selected_features.pkl
enrollment_status_encoder.pkl
target_label_encoder.pkl
```

After replacing model files, restart the backend.

## Useful Commands

Compile without running tests:

```powershell
.\mvnw.cmd -DskipTests compile
```

Run tests:

```powershell
.\mvnw.cmd test
```

Run the backend:

```powershell
.\mvnw.cmd spring-boot:run
```

## Troubleshooting

If the frontend says it cannot connect to the server:

- Confirm backend is running on `http://localhost:8080`
- Confirm frontend is served from `http://localhost:5500`
- Check the browser console and backend logs

If prediction fails:

- Install Python dependencies: `pip install pandas scikit-learn joblib`
- Confirm all `.pkl` files are in the project root
- Set `ml.project-dir` to an absolute path if running from an IDE
- Restart the backend after changing model files

If seed data does not appear:

- Restart the backend
- Confirm MySQL credentials are correct
- Check the backend logs for JSON or database constraint errors

## Environment Variables

Optional local environment variables:

```text
DB_USERNAME=root
DB_PASSWORD=
MAIL_USERNAME=your-email@example.com
MAIL_PASSWORD=your-app-password
JWT_SECRET=replace-with-a-long-random-secret
```

Do not commit real mail passwords, database passwords, or production JWT secrets.
