// Reveals sections as they scroll into view. The page head marks <html> with "js" first.

const observer = new IntersectionObserver((entries) => {
  for (const entry of entries) {
    if (!entry.isIntersecting) continue;
    entry.target.classList.add("in");
    observer.unobserve(entry.target);
  }
}, { rootMargin: "0px 0px -10% 0px", threshold: 0.12 });

document.querySelectorAll(".reveal").forEach((element, index) => {
  // Siblings that enter together arrive in order.
  element.style.transitionDelay = `${(index % 3) * 60}ms`;
  observer.observe(element);
});
