## Plan: Replace Course Emojis with Images

Here is a plan to change the course cards from using program-based emoji icons and gradients to displaying specific cover images based on the course itself.

### Steps
1. Create a new `getCourseImage(title)` JavaScript function to map course titles (or `id`) to your available filenames (`business.png`, `data.png`, `default.png`, `design.png`, `network.png`, `software.png`).
2. Open `course-catalog.html` and `my-courses.html`.
3. Replace the `getCourseEmoji()` and `getCourseGradient()` function calls inside the `renderCourses` loops with the new `getCourseImage(c.title)` function.
4. Replace the `<div class="course-thumbnail">` HTML contents by removing the emoji `<span>` and inline background style, inserting an `<img src="assets/courses/${imageName}" />` instead.
5. Add CSS styling to the new `<img>` tag (e.g., `width: 100%; height: 100%; object-fit: cover;`) so the images fit perfectly inside the thumbnail container.

### Further Considerations
1. Should we define `getCourseImage` inside `js/common.js` to ensure consistency across all pages that display course lists?
2. How exactly would you like to map the course titles to the image filenames (e.g., if the title contains "Java", return `software.png`)?

