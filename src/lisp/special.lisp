'abc

(defmacro gensym ()
  '(asm (:invokestatic "io/github/whetfire/lateral/Symbol"
                      "gensym"
                      "()Lio/github/whetfire/lateral/Symbol;")))

(gensym)
(gensym)
(gensym)

(defun list (:rest lst)
  lst)

(list 'a 'b 3 "test")

12345

(defun asdf ()
  'asdf)

(asdf)
