(if t (quote (1 2 nil)) (quote asdf))

(or nil 1 2 t)

(cons nil (if nil t nil))

(cons t (cons nil nil))
(if (if t t nil) t nil)
