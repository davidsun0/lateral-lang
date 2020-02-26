(cons 1 nil)

3

"hello world"

(defmacro x (y)
  (cons y (cons 1 (cons (quote nil) nil))))

(defun a (b)
  (x cons))

(a 1)
